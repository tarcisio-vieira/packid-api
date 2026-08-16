package com.packid.api.service;

import com.packid.api.controller.packid.dto.PackIdCreateRequest;
import com.packid.api.controller.packid.dto.PackIdLabelCreateRequest;
import com.packid.api.controller.packid.dto.PackIdRecentResponse;
import com.packid.api.controller.packid.dto.PackIdResponse;
import com.packid.api.controller.packid.dto.PackIdUpdateRequest;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.PackId;
import com.packid.api.domain.model.Person;
import com.packid.api.domain.model.ResidentialUnit;
import com.packid.api.domain.model.Condominium;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.CondominiumRepository;
import com.packid.api.domain.repository.AppUserRepository;
import com.packid.api.domain.repository.PackIdRepository;
import com.packid.api.domain.repository.PersonRepository;
import com.packid.api.domain.repository.ResidentialUnitRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.type.PackageType;
import com.packid.api.service.notification.UnitChangeNotificationPublisher;
import jakarta.transaction.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PackIdService {

    private final PackIdRepository repository;
    private final AppUserRepository appUserRepository;
    private final ResidentialUnitRepository residentialUnitRepository;
    private final PersonRepository personRepository;
    private final RegistryEntryRepository registryEntryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CondominiumRepository condominiumRepository;
    private final UnitChangeNotificationPublisher unitChangeNotificationPublisher;

    public PackIdService(
            PackIdRepository repository,
            AppUserRepository appUserRepository,
            ResidentialUnitRepository residentialUnitRepository,
            PersonRepository personRepository,
            RegistryEntryRepository registryEntryRepository,
            CondominiumRepository condominiumRepository,
            ApplicationEventPublisher eventPublisher,
            UnitChangeNotificationPublisher unitChangeNotificationPublisher
    ) {
        this.repository = repository;
        this.appUserRepository = appUserRepository;
        this.residentialUnitRepository = residentialUnitRepository;
        this.personRepository = personRepository;
        this.registryEntryRepository = registryEntryRepository;
        this.condominiumRepository = condominiumRepository;
        this.eventPublisher = eventPublisher;
        this.unitChangeNotificationPublisher = unitChangeNotificationPublisher;
    }

    @Transactional
    public PackIdResponse create(PackIdCreateRequest req, String actor) {
        validateResidentialUnit(req.tenantId(), req.residentialUnitId());
        validateResidentPerson(req.tenantId(), req.personId());

        PackId p = new PackId();

        p.setTenantId(req.tenantId());
        p.setResidentialUnitId(req.residentialUnitId());
        p.setPersonId(req.personId());
        p.setRegisteredByUserId(req.registeredByUserId());

        p.setPackageType(req.packageType());
        p.setPackageCode(req.packageCode());
        p.setLabelPackageCode(req.packageCode());
        p.setBookPage(cleanOptional(req.bookPage()));
        p.setBuildingBlock(cleanOptional(req.buildingBlock()));
        p.setApartment(cleanOptional(req.apartment()));
        p.setCarrier(req.carrier());
        p.setTrackingCode(req.trackingCode());
        p.setDescription(req.description());

        p.setArrivedAt(req.arrivedAt());
        p.setObservations(req.observations());
        p.setCreatedBy(normalizeActor(actor));

        PackId saved = repository.save(p);

        eventPublisher.publishEvent(new PackIdCreatedEvent(saved.getTenantId(), saved.getId()));
        publishPackIdEmail(saved, normalizeActor(actor));

        return toResponse(saved);
    }

    public PackIdResponse getById(UUID tenantId, UUID id) {
        PackId p = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PackId não encontrado"));
        return toResponse(p);
    }

    public List<PackIdResponse> getAll(UUID tenantId) {
        return repository.findAllByTenantIdAndDeletedFalse(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PackIdResponse update(UUID tenantId, UUID id, PackIdUpdateRequest req, String actor) {
        PackId p = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PackId não encontrado"));

        if (req.residentialUnitId() != null) {
            validateResidentialUnit(tenantId, req.residentialUnitId());
            p.setResidentialUnitId(req.residentialUnitId());
        }

        if (req.personId() != null) {
            validateResidentPerson(tenantId, req.personId());
            p.setPersonId(req.personId());
        }

        if (req.registeredByUserId() != null) p.setRegisteredByUserId(req.registeredByUserId());

        if (req.packageType() != null) p.setPackageType(req.packageType());
        if (req.packageCode() != null) {
            p.setPackageCode(req.packageCode());
            p.setLabelPackageCode(req.packageCode());
        }
        if (req.bookPage() != null) p.setBookPage(cleanOptional(req.bookPage()));
        if (req.buildingBlock() != null) p.setBuildingBlock(cleanOptional(req.buildingBlock()));
        if (req.apartment() != null) p.setApartment(cleanOptional(req.apartment()));
        if (req.carrier() != null) p.setCarrier(req.carrier());
        if (req.trackingCode() != null) p.setTrackingCode(req.trackingCode());
        if (req.description() != null) p.setDescription(req.description());

        if (req.arrivedAt() != null) p.setArrivedAt(req.arrivedAt());

        if (req.whatsappMessageId() != null) p.setWhatsappMessageId(req.whatsappMessageId());
        if (req.whatsappSentAt() != null) p.setWhatsappSentAt(req.whatsappSentAt());
        if (req.whatsappDeliveredAt() != null) p.setWhatsappDeliveredAt(req.whatsappDeliveredAt());
        if (req.whatsappReadAt() != null) p.setWhatsappReadAt(req.whatsappReadAt());

        if (req.residentAcknowledgedAt() != null) p.setResidentAcknowledgedAt(req.residentAcknowledgedAt());

        if (req.handedOverAt() != null) p.setHandedOverAt(req.handedOverAt());
        if (req.handedOverByUserId() != null) p.setHandedOverByUserId(req.handedOverByUserId());

        if (req.observations() != null) p.setObservations(req.observations());

        p.setUpdatedBy(normalizeActor(actor));

        PackId saved = repository.save(p);
        return toResponse(saved);
    }

    @Transactional
    public void logicalDelete(UUID tenantId, UUID id, String actor) {
        PackId p = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PackId não encontrado"));

        p.setDeleted(true);
        p.setDeletedAt(LocalDateTime.now());
        p.setDeletedBy(normalizeActor(actor));

        repository.save(p);
    }

    @Transactional
    public PackIdResponse createFromLabel(OidcUser oidcUser, PackIdLabelCreateRequest req) {
        if (oidcUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }

        String email = (oidcUser.getEmail() != null) ? oidcUser.getEmail().trim() : null;
        String subject = (oidcUser.getSubject() != null) ? oidcUser.getSubject().trim() : null;

        AppUser appUser = resolveAppUser(email, subject);
        UUID tenantId = appUser.getTenantId();

        String apartment = req.apartment().trim();
        String block = req.block().trim();
        String bookPage = req.bookPage().trim();
        String packageCode = req.packageCode().trim();

        validateLabelUnit(bookPage, block, apartment);

        ResidentialUnit unit = resolveOrCreateResidentialUnit(tenantId, block, apartment);
        Person resident = resolveResidentForUnit(tenantId, block, apartment);

        PackId p = new PackId();
        p.setTenantId(tenantId);
        p.setResidentialUnitId(unit.getId());
        p.setPersonId(resident.getId());
        p.setRegisteredByUserId(appUser.getId());

        p.setPackageType(PackageType.PACKAGE);
        p.setPackageCode(packageCode);
        p.setLabelPackageCode(packageCode);
        p.setBookPage(bookPage);
        p.setBuildingBlock(block);
        p.setApartment(apartment);
        p.setObservations("x ");

        if (email != null && !email.isBlank()) {
            p.setCreatedBy(email);
        }

        try {
            PackId saved = repository.save(p);

            eventPublisher.publishEvent(new PackIdCreatedEvent(saved.getTenantId(), saved.getId()));
            publishPackIdEmail(saved, email == null || email.isBlank() ? "sistema" : email);

            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Não foi possível salvar o pacote."
            );
        }
    }


    private void publishPackIdEmail(PackId packId, String actor) {
        String block = cleanOptional(packId.getBuildingBlock());
        String apartment = cleanOptional(packId.getApartment());
        if (block == null || apartment == null) return;

        String code = cleanOptional(packId.getLabelPackageCode());
        if (code == null) code = cleanOptional(packId.getPackageCode());

        StringBuilder details = new StringBuilder("Uma nova encomenda foi registrada para a unidade.");
        if (code != null) details.append(" Código da encomenda: ").append(code).append('.');
        if (cleanOptional(packId.getBookPage()) != null) {
            details.append(" Página: ").append(packId.getBookPage()).append('.');
        }

        unitChangeNotificationPublisher.publish(
                packId.getTenantId(),
                block,
                apartment,
                List.of(),
                "PACKID_RECEIVED",
                "Encomenda recebida",
                details.toString(),
                actor
        );
    }

    private ResidentialUnit validateResidentialUnit(UUID tenantId, UUID residentialUnitId) {
        return residentialUnitRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, residentialUnitId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unidade residencial não encontrada neste tenant"
                ));
    }

    private Person validateResidentPerson(UUID tenantId, UUID personId) {
        return personRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, personId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Pessoa não encontrada neste tenant"
                ));
    }

    private PackIdResponse toResponse(PackId p) {
        return new PackIdResponse(
                p.getId(),
                p.getTenantId(),
                p.getResidentialUnitId(),
                p.getPersonId(),
                p.getRegisteredByUserId(),

                p.getPackageType(),

                p.getPackageCode(),
                p.getPackageCodeHash(),
                p.getBookPage(),
                p.getBuildingBlock(),
                p.getApartment(),

                p.getCarrier(),
                p.getTrackingCode(),
                p.getDescription(),

                p.getArrivedAt(),

                p.getWhatsappMessageId(),
                p.getWhatsappSentAt(),
                p.getWhatsappDeliveredAt(),
                p.getWhatsappReadAt(),

                p.getResidentAcknowledgedAt(),

                p.getHandedOverAt(),
                p.getHandedOverByUserId(),

                p.getObservations(),

                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private String normalizeActor(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor.trim();
    }

    private AppUser resolveAppUser(String email, String subject) {
        if (subject != null && !subject.isBlank()) {
            List<AppUser> bySub = appUserRepository
                    .findAllByProviderAndProviderSubjectAndDeletedFalse(AppUser.AuthProvider.GOOGLE, subject);

            if (bySub.size() == 1) return bySub.get(0);

            if (bySub.size() > 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Google subject encontrado em mais de um AppUser. Ajuste o modelo/seed para ficar único."
                );
            }
        }

        if (email != null && !email.isBlank()) {
            List<AppUser> byEmail = appUserRepository.findAllByEmailAndDeletedFalse(email);

            if (byEmail.size() == 1) return byEmail.get(0);

            if (byEmail.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Não encontrei AppUser para o email: " + email
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email existe em mais de um AppUser. Para ficar determinístico, prefira provider_subject (sub)."
            );
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "OIDC sem email e sem subject (sub).");
    }

    @Transactional
    public List<PackIdRecentResponse> getRecentForMe(OidcUser oidcUser, int limit, Instant from, Instant to) {
        AppUser appUser = resolveAppUser(oidcUser.getEmail(), oidcUser.getSubject());
        UUID tenantId = appUser.getTenantId();

        int safeLimit = Math.min(Math.max(limit, 1), 200);

        java.sql.Timestamp fromTs = (from == null) ? null : java.sql.Timestamp.from(from);
        java.sql.Timestamp toTs = (to == null) ? null : java.sql.Timestamp.from(to);

        return repository.findRecentByTenant(tenantId, safeLimit, fromTs, toTs).stream()
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
                .toList();
    }

    private String cleanOptional(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private void validateLabelUnit(String bookPage, String block, String apartment) {
        if (!bookPage.matches("(?:00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Página inválida. Informe de 001 até 999.");
        }

        if (!block.matches("[1-4]")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bloco inválido. Informe um bloco de 1 a 4.");
        }

        if (!apartment.matches("(?:[1-9][0-9]{2}|1[0-2][0-9]{2})")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Apartamento inválido. O número deve representar um apartamento do 1º ao 12º andar."
            );
        }
    }

    private Person resolveResidentForUnit(UUID tenantId, String block, String apartment) {
        List<RegistryEntry> residents;

        if (block != null) {
            residents = registryEntryRepository
                    .findAllByTenantIdAndEntryTypeAndBlockIgnoreCaseAndApartmentIgnoreCaseAndActiveTrueAndDeletedFalseOrderByNameAsc(
                            tenantId, RegistryEntry.EntryType.RESIDENT, block, apartment);
        } else {
            residents = registryEntryRepository
                    .findAllByTenantIdAndEntryTypeAndApartmentIgnoreCaseAndActiveTrueAndDeletedFalseOrderByNameAsc(
                            tenantId, RegistryEntry.EntryType.RESIDENT, apartment);
        }

        if (residents.size() == 1 && residents.get(0).getPersonId() != null) {
            return personRepository
                    .findByTenantIdAndIdAndDeletedFalse(tenantId, residents.get(0).getPersonId())
                    .orElseGet(() -> resolveOrCreateSymbolicResident(tenantId));
        }

        return resolveOrCreateSymbolicResident(tenantId);
    }

    private static final String SYMBOLIC_RESIDENT_NAME = "***";

    private ResidentialUnit resolveOrCreateResidentialUnit(UUID tenantId, String block, String apartment) {
        String unitCode = block + apartment;
        List<ResidentialUnit> units =
                residentialUnitRepository.findAllByTenantIdAndCodeAndDeletedFalse(tenantId, unitCode);

        if (units.size() == 1) {
            return units.get(0);
        }

        if (units.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Existe mais de uma unidade com o código '" + unitCode + "' neste tenant."
            );
        }

        Condominium condominium = resolveDefaultCondominium(tenantId);

        ResidentialUnit unit = new ResidentialUnit();
        unit.setTenantId(tenantId);
        unit.setCondominiumId(condominium.getId());
        unit.setCode(unitCode);
        unit.setName("Unidade criada automaticamente");
        unit.setActive(true);

        return residentialUnitRepository.save(unit);
    }

    private Condominium resolveDefaultCondominium(UUID tenantId) {
        List<Condominium> condominiums = condominiumRepository.findAllByTenantIdAndDeletedFalse(tenantId);

        if (condominiums.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Nenhum condomínio encontrado para o tenant"
            );
        }

        if (condominiums.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Há mais de um condomínio para este tenant. " +
                            "Para cadastro automático, será preciso informar bloco/condomínio."
            );
        }

        return condominiums.get(0);
    }

    private Person resolveOrCreateSymbolicResident(UUID tenantId) {
        return personRepository
                .findFirstByTenantIdAndFullNameAndDeletedFalse(tenantId, SYMBOLIC_RESIDENT_NAME)
                .orElseGet(() -> {
                    Person person = new Person();
                    person.setTenantId(tenantId);
                    person.setFullName(SYMBOLIC_RESIDENT_NAME);
                    person.setPersonType(Person.PersonType.RESIDENT);
                    return personRepository.save(person);
                });
    }
}