package com.packid.api.service;

import com.packid.api.controller.occupancy.dto.ApartmentOccupancyResponse;
import com.packid.api.controller.packid.dto.PackIdRecentResponse;
import com.packid.api.controller.registry.dto.RegistryEntryRequest;
import com.packid.api.controller.registry.dto.RegistryEntryResponse;
import com.packid.api.controller.registry.dto.UnitRegistrySummaryResponse;
import com.packid.api.domain.model.ApartmentOccupancy;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.RegistryEntry.EntryType;
import com.packid.api.domain.model.Person;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.repository.PackIdRepository;
import com.packid.api.domain.repository.PersonRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RegistryEntryService {

    private final RegistryEntryRepository repository;
    private final PersonRepository personRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final VisitorVisitService visitorVisitService;
    private final DeliveryRecordService deliveryRecordService;
    private final PackIdRepository packIdRepository;
    private final ApartmentOccupancyService occupancyService;

    public RegistryEntryService(
            RegistryEntryRepository repository,
            PersonRepository personRepository,
            AuthenticatedUserService authenticatedUserService,
            VisitorVisitService visitorVisitService,
            DeliveryRecordService deliveryRecordService,
            PackIdRepository packIdRepository,
            ApartmentOccupancyService occupancyService
    ) {
        this.repository = repository;
        this.personRepository = personRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.visitorVisitService = visitorVisitService;
        this.deliveryRecordService = deliveryRecordService;
        this.packIdRepository = packIdRepository;
        this.occupancyService = occupancyService;
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

    public UnitRegistrySummaryResponse getUnitSummary(OidcUser oidcUser, String block, String apartment, UUID occupancyId) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        String cleanedBlock = cleanRequiredUnit(block, "Bloco é obrigatório.");
        String cleanedApartment = cleanRequiredUnit(apartment, "Apartamento é obrigatório.");

        List<ApartmentOccupancy> occupancies = occupancyService.listByUnit(appUser, cleanedBlock, cleanedApartment);
        ApartmentOccupancy selectedOccupancy = null;

        if (occupancyId != null) {
            selectedOccupancy = occupancyService.findById(appUser, occupancyId);
            if (!sameUnit(selectedOccupancy, cleanedBlock, cleanedApartment)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A ocupação informada não pertence a esta unidade.");
            }
        } else {
            selectedOccupancy = occupancies.stream()
                    .filter(item -> item.getStatus() == ApartmentOccupancy.Status.ACTIVE)
                    .findFirst()
                    .orElse(occupancies.isEmpty() ? null : occupancies.get(0));
        }

        List<RegistryEntry> unitEntries;
        LocalDateTime from = null;
        LocalDateTime to = null;

        if (selectedOccupancy != null) {
            unitEntries = repository.findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByNameAsc(
                    appUser.getTenantId(), selectedOccupancy.getId());
            if (selectedOccupancy.getStatus() == ApartmentOccupancy.Status.ACTIVE) {
                unitEntries = unitEntries.stream().filter(item -> Boolean.TRUE.equals(item.getActive())).toList();
            }
            from = selectedOccupancy.getStartDate().atStartOfDay();
            if (selectedOccupancy.getEndDate() != null) {
                to = selectedOccupancy.getEndDate().plusDays(1).atStartOfDay();
            }
        } else {
            unitEntries = repository.findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndActiveTrueAndDeletedFalseOrderByNameAsc(
                    appUser.getTenantId(), cleanedBlock, cleanedApartment);
        }

        LocalDateTime finalFrom = from;
        LocalDateTime finalTo = to;
        ApartmentOccupancyResponse selectedResponse = occupancyService.toResponse(selectedOccupancy);
        List<ApartmentOccupancyResponse> occupancyResponses = occupancies.stream().map(occupancyService::toResponse).toList();

        return new UnitRegistrySummaryResponse(
                cleanedBlock,
                cleanedApartment,
                selectedResponse,
                occupancyResponses,
                unitEntries.stream().filter(e -> e.getEntryType() == EntryType.RESIDENT).map(e -> toResponse(e, appUser)).toList(),
                unitEntries.stream().filter(e -> e.getEntryType() == EntryType.BICYCLE).map(e -> toResponse(e, appUser)).toList(),
                unitEntries.stream().filter(e -> e.getEntryType() == EntryType.VEHICLE).map(e -> toResponse(e, appUser)).toList(),
                unitEntries.stream().filter(e -> e.getEntryType() == EntryType.PET).map(e -> toResponse(e, appUser)).toList(),
                visitorVisitService.getByUnit(appUser, cleanedBlock, cleanedApartment, finalFrom, finalTo),
                deliveryRecordService.getByUnit(appUser, cleanedBlock, cleanedApartment, finalFrom, finalTo),
                packIdRepository.findByUnit(
                                appUser.getTenantId(),
                                cleanedBlock,
                                cleanedApartment,
                                finalFrom == null ? null : Timestamp.valueOf(finalFrom),
                                finalTo == null ? null : Timestamp.valueOf(finalTo),
                                200
                        ).stream()
                        .map(r -> new PackIdRecentResponse(
                                r.getId(),
                                r.getBookPage(),
                                r.getBlock(),
                                r.getApartment(),
                                r.getResidentFullName(),
                                r.getPackageCode(),
                                r.getLabelPackageCode(),
                                r.getObservations(),
                                r.getArrivedAt(),
                                r.getCreatedBy()
                        ))
                        .toList()
        );
    }

    private boolean sameUnit(ApartmentOccupancy occupancy, String block, String apartment) {
        return occupancy != null
                && occupancy.getBlock().trim().equalsIgnoreCase(block.trim())
                && occupancy.getApartment().trim().equalsIgnoreCase(apartment.trim());
    }

    @Transactional
    public RegistryEntryResponse create(OidcUser oidcUser, RegistryEntryRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);

        RegistryEntry entry = findReusableAccessPerson(appUser, request);
        boolean existing = entry != null;
        if (!existing) {
            entry = new RegistryEntry();
            entry.setTenantId(appUser.getTenantId());
            entry.setCreatedBy(actor(appUser));
        }

        apply(entry, request);
        syncOccupancy(appUser, entry);
        syncResidentPerson(appUser, entry);
        if (existing) {
            entry.setUpdatedBy(actor(appUser));
        }

        return toResponse(repository.save(entry), appUser);
    }

    @Transactional
    public RegistryEntryResponse update(OidcUser oidcUser, UUID id, RegistryEntryRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        apply(entry, request);
        syncOccupancy(appUser, entry);
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

    private RegistryEntry findReusableAccessPerson(AppUser appUser, RegistryEntryRequest request) {
        if (request.entryType() != EntryType.VISITOR && request.entryType() != EntryType.DELIVERY_PERSON) {
            return null;
        }
        String document = clean(request.document());
        if (document == null) return null;

        return repository.findByTenantIdAndEntryTypeAndDocumentIgnoreCaseAndDeletedFalse(
                appUser.getTenantId(), request.entryType(), document).orElse(null);
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

        if ((entry.getEntryType() == EntryType.RESIDENT
                || entry.getEntryType() == EntryType.BICYCLE
                || entry.getEntryType() == EntryType.PET
                || entry.getEntryType() == EntryType.VEHICLE)
                && Boolean.TRUE.equals(entry.getActive())
                && (entry.getBlock() == null || entry.getApartment() == null)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Para cadastros vinculados à ocupação, informe bloco e apartamento."
            );
        }
    }

    private void syncOccupancy(AppUser appUser, RegistryEntry entry) {
        if (!occupancyService.isOccupancyManagedType(entry.getEntryType())) {
            entry.setOccupancyId(null);
            return;
        }

        if (entry.getBlock() == null || entry.getApartment() == null) {
            if (entry.getEntryType() == EntryType.RESIDENT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Para condômino, informe bloco e apartamento.");
            }
            entry.setOccupancyId(null);
            return;
        }

        if (Boolean.TRUE.equals(entry.getActive())) {
            ApartmentOccupancy occupancy = occupancyService.ensureActiveOccupancy(
                    appUser, entry.getBlock(), entry.getApartment());
            entry.setOccupancyId(occupancy.getId());
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
                entry.getOccupancyId(),
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

    private String cleanRequiredUnit(String value, String message) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return cleaned;
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
