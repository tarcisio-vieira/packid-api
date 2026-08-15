package com.packid.api.service;

import com.packid.api.controller.registry.dto.RegistryEntryRequest;
import com.packid.api.controller.registry.dto.RegistryEntryResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.RegistryEntry.EntryType;
import com.packid.api.domain.model.Person;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.repository.PersonRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RegistryEntryService {

    private final RegistryEntryRepository repository;
    private final PersonRepository personRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public RegistryEntryService(
            RegistryEntryRepository repository,
            PersonRepository personRepository,
            AuthenticatedUserService authenticatedUserService
    ) {
        this.repository = repository;
        this.personRepository = personRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    public List<RegistryEntryResponse> getAll(OidcUser oidcUser, EntryType entryType) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        List<RegistryEntry> entries = entryType == null
                ? repository.findAllByTenantIdAndDeletedFalseOrderByNameAsc(appUser.getTenantId())
                : repository.findAllByTenantIdAndEntryTypeAndDeletedFalseOrderByNameAsc(appUser.getTenantId(), entryType);

        return entries.stream().map(entry -> toResponse(entry, appUser)).toList();
    }

    public RegistryEntryResponse getById(OidcUser oidcUser, UUID id) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
        return toResponse(entry, appUser);
    }

    @Transactional
    public RegistryEntryResponse create(OidcUser oidcUser, RegistryEntryRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);

        RegistryEntry entry = new RegistryEntry();
        entry.setTenantId(appUser.getTenantId());
        apply(entry, request);
        syncResidentPerson(appUser, entry);
        entry.setCreatedBy(actor(appUser));

        return toResponse(repository.save(entry), appUser);
    }

    @Transactional
    public RegistryEntryResponse update(OidcUser oidcUser, UUID id, RegistryEntryRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        apply(entry, request);
        syncResidentPerson(appUser, entry);
        entry.setUpdatedBy(actor(appUser));
        return toResponse(repository.save(entry), appUser);
    }

    @Transactional
    public void delete(OidcUser oidcUser, UUID id) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        entry.setDeleted(true);
        entry.setDeletedAt(LocalDateTime.now());
        entry.setDeletedBy(actor(appUser));
        repository.save(entry);
    }

    private void apply(RegistryEntry entry, RegistryEntryRequest request) {
        entry.setEntryType(request.entryType());
        entry.setName(cleanRequired(request.name()));
        entry.setDocument(clean(request.document()));
        entry.setPhone(clean(request.phone()));
        entry.setEmail(clean(request.email()));
        entry.setBlock(clean(request.block()));
        entry.setApartment(clean(request.apartment()));
        entry.setCompany(clean(request.company()));
        entry.setOwnerName(clean(request.ownerName()));
        entry.setBrand(clean(request.brand()));
        entry.setModel(clean(request.model()));
        entry.setColor(clean(request.color()));
        entry.setIdentifier(clean(request.identifier()));
        entry.setSpecies(clean(request.species()));
        entry.setBreed(clean(request.breed()));
        entry.setParkingSpace(clean(request.parkingSpace()));
        entry.setNotes(clean(request.notes()));
        entry.setActive(request.active() == null ? Boolean.TRUE : request.active());

        if (entry.getEntryType() == EntryType.RESIDENT
                && (entry.getBlock() == null || entry.getApartment() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Para condômino, informe bloco/página e apartamento."
            );
        }
    }

    private void syncResidentPerson(AppUser appUser, RegistryEntry entry) {
        if (entry.getEntryType() != EntryType.RESIDENT) {
            entry.setPersonId(null);
            return;
        }

        UUID tenantId = appUser.getTenantId();
        Person person = null;

        if (entry.getPersonId() != null) {
            person = personRepository
                    .findByTenantIdAndIdAndDeletedFalse(tenantId, entry.getPersonId())
                    .orElse(null);
        }

        if (entry.getDocument() != null) {
            Person byDocument = personRepository
                    .findByTenantIdAndDocumentAndDeletedFalse(tenantId, entry.getDocument())
                    .orElse(null);

            if (person == null) {
                person = byDocument;
            } else if (byDocument != null && !byDocument.getId().equals(person.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe outro condômino com este documento."
                );
            }
        }

        if (entry.getEmail() != null) {
            Person byEmail = personRepository
                    .findByTenantIdAndEmailAndDeletedFalse(tenantId, entry.getEmail())
                    .orElse(null);

            if (person == null) {
                person = byEmail;
            } else if (byEmail != null && !byEmail.getId().equals(person.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe outro condômino com este e-mail."
                );
            }
        }

        boolean isNew = person == null;
        if (isNew) {
            person = new Person();
            person.setTenantId(tenantId);
            person.setCreatedBy(actor(appUser));
        } else {
            person.setUpdatedBy(actor(appUser));
        }

        person.setFullName(entry.getName());
        person.setDocument(entry.getDocument());
        person.setEmail(entry.getEmail());
        person.setPhone(entry.getPhone());
        person.setPersonType(Person.PersonType.RESIDENT);

        Person saved = personRepository.save(person);
        entry.setPersonId(saved.getId());
    }

    public RegistryEntryResponse toResponse(RegistryEntry entry, AppUser appUser) {
        return new RegistryEntryResponse(
                entry.getId(),
                entry.getPersonId(),
                entry.getEntryType(),
                entry.getName(),
                entry.getDocument(),
                entry.getPhone(),
                entry.getEmail(),
                entry.getBlock(),
                entry.getApartment(),
                entry.getCompany(),
                entry.getOwnerName(),
                entry.getBrand(),
                entry.getModel(),
                entry.getColor(),
                entry.getIdentifier(),
                entry.getSpecies(),
                entry.getBreed(),
                entry.getParkingSpace(),
                entry.getNotes(),
                entry.getPhotoDriveFileId() != null && !entry.getPhotoDriveFileId().isBlank(),
                sameEmail(entry.getPhotoOwnerEmail(), appUser.getEmail()),
                entry.getPhotoFileName(),
                entry.getActive(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }


    private boolean sameEmail(String left, String right) {
        String a = clean(left);
        String b = clean(right);
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String actor(AppUser appUser) {
        if (appUser.getEmail() != null && !appUser.getEmail().isBlank()) {
            return appUser.getEmail().trim();
        }
        return "system";
    }

    private String cleanRequired(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome/identificação é obrigatório.");
        }
        return cleaned;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
