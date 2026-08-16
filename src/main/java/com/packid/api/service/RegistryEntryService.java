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
import com.packid.api.domain.repository.ServiceCompanyRepository;
import com.packid.api.service.notification.UnitChangeNotificationPublisher;
import com.packid.api.integration.google.TenantGoogleAccountService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
    private final UnitChangeNotificationPublisher unitChangeNotificationPublisher;
    private final TenantGoogleAccountService googleAccountService;
    private final ServiceRecordService serviceRecordService;
    private final ServiceCompanyRepository serviceCompanyRepository;

    public RegistryEntryService(
            RegistryEntryRepository repository,
            PersonRepository personRepository,
            AuthenticatedUserService authenticatedUserService,
            VisitorVisitService visitorVisitService,
            DeliveryRecordService deliveryRecordService,
            PackIdRepository packIdRepository,
            ApartmentOccupancyService occupancyService,
            UnitChangeNotificationPublisher unitChangeNotificationPublisher,
            TenantGoogleAccountService googleAccountService,
            ServiceRecordService serviceRecordService,
            ServiceCompanyRepository serviceCompanyRepository
    ) {
        this.repository = repository;
        this.personRepository = personRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.visitorVisitService = visitorVisitService;
        this.deliveryRecordService = deliveryRecordService;
        this.packIdRepository = packIdRepository;
        this.occupancyService = occupancyService;
        this.unitChangeNotificationPublisher = unitChangeNotificationPublisher;
        this.googleAccountService = googleAccountService;
        this.serviceRecordService = serviceRecordService;
        this.serviceCompanyRepository = serviceCompanyRepository;
    }

    public List<RegistryEntryResponse> getAll(OidcUser oidcUser, EntryType entryType) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        List<RegistryEntry> entries = entryType == null
                ? repository.findAllByTenantIdAndDeletedFalseOrderByNameAsc(appUser.getTenantId())
                : repository.findAllByTenantIdAndEntryTypeAndDeletedFalseOrderByNameAsc(appUser.getTenantId(), entryType);

        String officialGoogleEmail = googleAccountService.getOfficialEmail(appUser.getTenantId());
        return entries.stream().map(entry -> toResponse(entry, appUser, officialGoogleEmail)).toList();
    }

    public RegistryEntryResponse getById(OidcUser oidcUser, UUID id) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
        String officialGoogleEmail = googleAccountService.getOfficialEmail(appUser.getTenantId());
        return toResponse(entry, appUser, officialGoogleEmail);
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
        String officialGoogleEmail = googleAccountService.getOfficialEmail(appUser.getTenantId());

        return new UnitRegistrySummaryResponse(
                cleanedBlock,
                cleanedApartment,
                selectedResponse,
                occupancyResponses,
                unitEntries.stream().filter(e -> e.getEntryType() == EntryType.RESIDENT).map(e -> toResponse(e, appUser, officialGoogleEmail)).toList(),
                unitEntries.stream().filter(e -> e.getEntryType() == EntryType.BICYCLE).map(e -> toResponse(e, appUser, officialGoogleEmail)).toList(),
                unitEntries.stream().filter(e -> e.getEntryType() == EntryType.VEHICLE).map(e -> toResponse(e, appUser, officialGoogleEmail)).toList(),
                unitEntries.stream().filter(e -> e.getEntryType() == EntryType.PET).map(e -> toResponse(e, appUser, officialGoogleEmail)).toList(),
                visitorVisitService.getByUnit(appUser, cleanedBlock, cleanedApartment, finalFrom, finalTo),
                deliveryRecordService.getByUnit(appUser, cleanedBlock, cleanedApartment, finalFrom, finalTo),
                serviceRecordService.getByUnit(appUser, cleanedBlock, cleanedApartment, finalFrom, finalTo),
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
        // Resolve a conta oficial antes de persistir/alterar RegistryEntry. Assim evitamos
        // consultas adicionais que provoquem auto-flush enquanto uma nova entidade ainda
        // está sendo processada pelo Hibernate.
        String officialGoogleEmail = googleAccountService.getOfficialEmail(appUser.getTenantId());

        RegistryEntry entry = findReusableAccessPerson(appUser, request);
        boolean existing = entry != null;
        EntrySnapshot before = existing ? snapshot(entry) : null;
        if (!existing) {
            entry = new RegistryEntry();
            entry.setTenantId(appUser.getTenantId());
            entry.setCreatedBy(actor(appUser));
        }

        apply(entry, request);
        syncServiceCompany(appUser, entry);
        syncOccupancy(appUser, entry);
        syncResidentPerson(appUser, entry);
        if (existing) {
            entry.setUpdatedBy(actor(appUser));
        }

        RegistryEntry saved = repository.save(entry);
        if (existing) {
            notifyUpdated(appUser, before, saved);
        } else {
            notifyCreated(appUser, saved);
        }
        return toResponse(saved, appUser, officialGoogleEmail);
    }

    @Transactional
    public RegistryEntryResponse update(OidcUser oidcUser, UUID id, RegistryEntryRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        String officialGoogleEmail = googleAccountService.getOfficialEmail(appUser.getTenantId());
        RegistryEntry entry = repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        EntrySnapshot before = snapshot(entry);
        apply(entry, request);
        syncServiceCompany(appUser, entry);
        syncOccupancy(appUser, entry);
        syncResidentPerson(appUser, entry);
        entry.setUpdatedBy(actor(appUser));
        RegistryEntry saved = repository.save(entry);
        notifyUpdated(appUser, before, saved);
        return toResponse(saved, appUser, officialGoogleEmail);
    }

    @Transactional
    public void delete(OidcUser oidcUser, UUID id) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        EntrySnapshot before = snapshot(entry);
        entry.setDeleted(true);
        entry.setDeletedAt(LocalDateTime.now());
        entry.setDeletedBy(actor(appUser));
        repository.save(entry);
        notifyDeleted(appUser, before);
    }

    private RegistryEntry findReusableAccessPerson(AppUser appUser, RegistryEntryRequest request) {
        if (request.entryType() != EntryType.VISITOR
                && request.entryType() != EntryType.DELIVERY_PERSON
                && request.entryType() != EntryType.SERVICE_PROVIDER) {
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
        entry.setUnitOwner(request.unitOwner() == null ? Boolean.FALSE : request.unitOwner());
        entry.setBirthDate(request.birthDate());
        entry.setProfession(clean(request.profession()));
        entry.setPne(request.pne() == null ? Boolean.FALSE : request.pne());
        entry.setBlock(clean(request.block()));
        entry.setApartment(clean(request.apartment()));
        entry.setCompany(clean(request.company()));
        entry.setServiceCompanyId(request.serviceCompanyId());
        entry.setOwnerName(clean(request.ownerName()));
        entry.setBrand(clean(request.brand()));
        entry.setModel(clean(request.model()));
        entry.setColor(clean(request.color()));
        entry.setIdentifier(clean(request.identifier()));
        if (entry.getEntryType() == EntryType.SERVICE_PROVIDER || entry.getEntryType() == EntryType.DELIVERY_PERSON) {
            // Prestador/entregador usa um único número de documento (CPF, CNH, RG ou outro).
            entry.setIdentifier(null);
        }
        entry.setSpecies(clean(request.species()));
        entry.setBreed(clean(request.breed()));
        entry.setPetSize(clean(request.petSize()));
        entry.setParkingSpace(clean(request.parkingSpace()));
        entry.setParkingSpaceRented(request.parkingSpaceRented() == null ? Boolean.FALSE : request.parkingSpaceRented());
        entry.setParkingSpaceRentalNotes(clean(request.parkingSpaceRentalNotes()));
        entry.setNotes(clean(request.notes()));
        entry.setActive(request.active() == null ? Boolean.TRUE : request.active());

        if (entry.getEntryType() != EntryType.RESIDENT) {
            entry.setUnitOwner(false);
            entry.setBirthDate(null);
            entry.setProfession(null);
            entry.setPne(false);
        }
        if (entry.getEntryType() != EntryType.PET) {
            entry.setPetSize(null);
        }
        if (entry.getEntryType() != EntryType.VEHICLE) {
            entry.setParkingSpaceRented(false);
            entry.setParkingSpaceRentalNotes(null);
        } else if (!Boolean.TRUE.equals(entry.getParkingSpaceRented())) {
            entry.setParkingSpaceRentalNotes(null);
        }

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

    private void syncServiceCompany(AppUser appUser, RegistryEntry entry) {
        boolean usesCompanyRegistry = entry.getEntryType() == EntryType.SERVICE_PROVIDER
                || entry.getEntryType() == EntryType.DELIVERY_PERSON;
        if (!usesCompanyRegistry) {
            entry.setServiceCompanyId(null);
            return;
        }
        if (entry.getServiceCompanyId() == null) {
            String message = entry.getEntryType() == EntryType.DELIVERY_PERSON
                    ? "Selecione a empresa/transportadora do entregador."
                    : "Selecione a empresa do prestador de serviço.";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        var company = serviceCompanyRepository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), entry.getServiceCompanyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empresa não encontrada."));
        if (!Boolean.TRUE.equals(company.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A empresa selecionada está inativa.");
        }
        entry.setCompany(company.getName());
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
        person.setPersonType(Boolean.TRUE.equals(entry.getUnitOwner())
                ? Person.PersonType.OWNER
                : Person.PersonType.RESIDENT);

        Person saved = personRepository.save(person);
        entry.setPersonId(saved.getId());
    }

    private void notifyCreated(AppUser appUser, RegistryEntry entry) {
        if (!occupancyService.isOccupancyManagedType(entry.getEntryType())) return;
        if (!Boolean.TRUE.equals(entry.getActive())) return;
        unitChangeNotificationPublisher.publish(
                appUser.getTenantId(),
                entry.getBlock(),
                entry.getApartment(),
                residentExtra(entry.getEntryType(), entry.getEmail()),
                "REGISTRY_CREATED",
                label(entry.getEntryType()) + " incluído",
                "Foi incluído " + labelLower(entry.getEntryType()) + " \"" + entry.getName() + "\" na unidade.",
                actor(appUser)
        );
    }

    private void notifyUpdated(AppUser appUser, EntrySnapshot before, RegistryEntry after) {
        if (before == null) return;
        boolean oldManaged = occupancyService.isOccupancyManagedType(before.entryType());
        boolean newManaged = occupancyService.isOccupancyManagedType(after.getEntryType());
        if (!oldManaged && !newManaged) return;

        boolean sameUnit = sameUnit(before.block(), before.apartment(), after.getBlock(), after.getApartment());
        if (!sameUnit) {
            if (oldManaged) {
                unitChangeNotificationPublisher.publish(
                        appUser.getTenantId(),
                        before.block(),
                        before.apartment(),
                        residentExtra(before.entryType(), before.email()),
                        "REGISTRY_MOVED_FROM",
                        label(before.entryType()) + " transferido de unidade",
                        "O cadastro \"" + before.name() + "\" foi transferido desta unidade.",
                        actor(appUser)
                );
            }
            if (newManaged && Boolean.TRUE.equals(after.getActive())) {
                unitChangeNotificationPublisher.publish(
                        appUser.getTenantId(),
                        after.getBlock(),
                        after.getApartment(),
                        residentExtra(after.getEntryType(), after.getEmail()),
                        "REGISTRY_MOVED_TO",
                        label(after.getEntryType()) + " vinculado à unidade",
                        "O cadastro \"" + after.getName() + "\" foi vinculado a esta unidade.",
                        actor(appUser)
                );
            }
            return;
        }

        String title;
        String details;
        if (Boolean.TRUE.equals(before.active()) && !Boolean.TRUE.equals(after.getActive())) {
            title = label(before.entryType()) + " inativado";
            details = "O cadastro \"" + before.name() + "\" foi inativado.";
        } else if (!Boolean.TRUE.equals(before.active()) && Boolean.TRUE.equals(after.getActive())) {
            title = label(after.getEntryType()) + " reativado";
            details = "O cadastro \"" + after.getName() + "\" foi reativado.";
        } else {
            List<String> fields = changedFields(before, after);
            if (fields.isEmpty()) return;
            title = label(after.getEntryType()) + " atualizado";
            details = "O cadastro \"" + after.getName() + "\" foi atualizado. Campos alterados: "
                    + String.join(", ", fields) + ".";
        }

        Collection<String> extras = new ArrayList<>();
        if (before.entryType() == EntryType.RESIDENT
                && Boolean.TRUE.equals(before.active())
                && !Boolean.TRUE.equals(after.getActive())
                && before.email() != null) {
            // Na inativação, o morador deixa de aparecer na consulta de ativos antes do evento ser enviado.
            // Mantemos o e-mail anterior apenas para que ele também receba a confirmação da própria saída.
            extras.add(before.email());
        }
        if (after.getEntryType() == EntryType.RESIDENT
                && Boolean.TRUE.equals(after.getActive())
                && after.getEmail() != null) {
            extras.add(after.getEmail());
        }

        unitChangeNotificationPublisher.publish(
                appUser.getTenantId(),
                after.getBlock() != null ? after.getBlock() : before.block(),
                after.getApartment() != null ? after.getApartment() : before.apartment(),
                extras,
                "REGISTRY_UPDATED",
                title,
                details,
                actor(appUser)
        );
    }

    private void notifyDeleted(AppUser appUser, EntrySnapshot before) {
        if (before == null || !occupancyService.isOccupancyManagedType(before.entryType())) return;
        unitChangeNotificationPublisher.publish(
                appUser.getTenantId(),
                before.block(),
                before.apartment(),
                residentExtra(before.entryType(), before.email()),
                "REGISTRY_DELETED",
                label(before.entryType()) + " removido",
                "O cadastro \"" + before.name() + "\" foi removido da unidade.",
                actor(appUser)
        );
    }

    private Collection<String> residentExtra(EntryType type, String email) {
        if (type == EntryType.RESIDENT && clean(email) != null) return List.of(email.trim());
        return List.of();
    }

    private List<String> changedFields(EntrySnapshot before, RegistryEntry after) {
        List<String> fields = new ArrayList<>();
        addChanged(fields, "nome/descrição", before.name(), after.getName());
        addChanged(fields, "documento/identificação", before.document(), after.getDocument());
        addChanged(fields, "telefone", before.phone(), after.getPhone());
        addChanged(fields, "e-mail", before.email(), after.getEmail());
        addChanged(fields, "proprietário da unidade", before.unitOwner(), after.getUnitOwner());
        addChanged(fields, "data de nascimento", before.birthDate(), after.getBirthDate());
        addChanged(fields, "profissão", before.profession(), after.getProfession());
        addChanged(fields, "PNE", before.pne(), after.getPne());
        addChanged(fields, "empresa", before.company(), after.getCompany());
        addChanged(fields, "responsável", before.ownerName(), after.getOwnerName());
        addChanged(fields, "marca", before.brand(), after.getBrand());
        addChanged(fields, "modelo", before.model(), after.getModel());
        addChanged(fields, "cor", before.color(), after.getColor());
        addChanged(fields, "identificação/placa", before.identifier(), after.getIdentifier());
        addChanged(fields, "espécie", before.species(), after.getSpecies());
        addChanged(fields, "raça", before.breed(), after.getBreed());
        addChanged(fields, "porte do pet", before.petSize(), after.getPetSize());
        addChanged(fields, "vaga", before.parkingSpace(), after.getParkingSpace());
        addChanged(fields, "vaga alugada/cedida", before.parkingSpaceRented(), after.getParkingSpaceRented());
        addChanged(fields, "detalhes da vaga alugada/cedida", before.parkingSpaceRentalNotes(), after.getParkingSpaceRentalNotes());
        addChanged(fields, "observação", before.notes(), after.getNotes());
        if (!Objects.equals(before.entryType(), after.getEntryType())) fields.add("tipo de cadastro");
        return fields;
    }

    private void addChanged(List<String> fields, String label, Object before, Object after) {
        if (!Objects.equals(normalize(before), normalize(after))) fields.add(label);
    }

    private Object normalize(Object value) {
        if (value instanceof String text) return clean(text);
        return value;
    }

    private EntrySnapshot snapshot(RegistryEntry entry) {
        return new EntrySnapshot(
                entry.getEntryType(), entry.getName(), entry.getDocument(), entry.getPhone(), entry.getEmail(),
                entry.getUnitOwner(), entry.getBirthDate(), entry.getProfession(), entry.getPne(),
                entry.getBlock(), entry.getApartment(), entry.getCompany(), entry.getOwnerName(), entry.getBrand(),
                entry.getModel(), entry.getColor(), entry.getIdentifier(), entry.getSpecies(), entry.getBreed(),
                entry.getPetSize(), entry.getParkingSpace(), entry.getParkingSpaceRented(), entry.getParkingSpaceRentalNotes(),
                entry.getNotes(), entry.getActive()
        );
    }

    private boolean sameUnit(String blockA, String apartmentA, String blockB, String apartmentB) {
        return Objects.equals(lowerClean(blockA), lowerClean(blockB))
                && Objects.equals(lowerClean(apartmentA), lowerClean(apartmentB));
    }

    private String lowerClean(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(java.util.Locale.ROOT);
    }

    private String label(EntryType type) {
        return switch (type) {
            case RESIDENT -> "Condômino";
            case BICYCLE -> "Bicicleta";
            case PET -> "Pet";
            case VEHICLE -> "Veículo";
            case DELIVERY_PERSON -> "Entregador";
            case VISITOR -> "Visitante";
            case SERVICE_PROVIDER -> "Prestador de serviço";
        };
    }

    private String labelLower(EntryType type) {
        return switch (type) {
            case RESIDENT -> "o condômino";
            case BICYCLE -> "a bicicleta";
            case PET -> "o pet";
            case VEHICLE -> "o veículo";
            case DELIVERY_PERSON -> "o entregador";
            case VISITOR -> "o visitante";
            case SERVICE_PROVIDER -> "o prestador de serviço";
        };
    }

    private record EntrySnapshot(
            EntryType entryType,
            String name,
            String document,
            String phone,
            String email,
            Boolean unitOwner,
            java.time.LocalDate birthDate,
            String profession,
            Boolean pne,
            String block,
            String apartment,
            String company,
            String ownerName,
            String brand,
            String model,
            String color,
            String identifier,
            String species,
            String breed,
            String petSize,
            String parkingSpace,
            Boolean parkingSpaceRented,
            String parkingSpaceRentalNotes,
            String notes,
            Boolean active
    ) {
    }

    public RegistryEntryResponse toResponse(RegistryEntry entry, AppUser appUser) {
        String officialGoogleEmail = googleAccountService.getOfficialEmail(appUser.getTenantId());
        return toResponse(entry, appUser, officialGoogleEmail);
    }

    private RegistryEntryResponse toResponse(RegistryEntry entry, AppUser appUser, String officialGoogleEmail) {
        return new RegistryEntryResponse(
                entry.getId(),
                entry.getPersonId(),
                entry.getOccupancyId(),
                entry.getEntryType(),
                entry.getName(),
                entry.getDocument(),
                entry.getPhone(),
                entry.getEmail(),
                entry.getUnitOwner(),
                entry.getBirthDate(),
                entry.getProfession(),
                entry.getPne(),
                entry.getBlock(),
                entry.getApartment(),
                entry.getCompany(),
                entry.getServiceCompanyId(),
                entry.getCompany(),
                entry.getOwnerName(),
                entry.getBrand(),
                entry.getModel(),
                entry.getColor(),
                entry.getIdentifier(),
                entry.getSpecies(),
                entry.getBreed(),
                entry.getPetSize(),
                entry.getParkingSpace(),
                entry.getParkingSpaceRented(),
                entry.getParkingSpaceRentalNotes(),
                entry.getNotes(),
                entry.getPhotoDriveFileId() != null && !entry.getPhotoDriveFileId().isBlank(),
                sameEmail(entry.getPhotoOwnerEmail(), appUser.getEmail())
                        || sameEmail(entry.getPhotoOwnerEmail(), officialGoogleEmail),
                entry.getPhotoFileName(),
                entry.getDocumentPhotoDriveFileId() != null && !entry.getDocumentPhotoDriveFileId().isBlank(),
                sameEmail(entry.getDocumentPhotoOwnerEmail(), appUser.getEmail()) || sameEmail(entry.getDocumentPhotoOwnerEmail(), officialGoogleEmail),
                entry.getDocumentPhotoFileName(),
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
