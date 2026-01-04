package com.packid.api.service;

import com.packid.api.controller.packid.dto.PackIdCreateRequest;
import com.packid.api.controller.packid.dto.PackIdRecentResponse;
import com.packid.api.controller.packid.dto.PackIdResponse;
import com.packid.api.controller.packid.dto.PackIdUpdateRequest;
import com.packid.api.domain.model.PackId;
import com.packid.api.domain.repository.PackIdRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.packid.api.controller.packid.dto.PackIdLabelCreateRequest;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.Person;
import com.packid.api.domain.model.ResidentialUnit;
import com.packid.api.domain.repository.AppUserRepository;
import com.packid.api.domain.repository.PersonRepository;
import com.packid.api.domain.repository.ResidentialUnitRepository;
import com.packid.api.domain.type.PackageType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

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

    public PackIdService(
            PackIdRepository repository,
            AppUserRepository appUserRepository,
            ResidentialUnitRepository residentialUnitRepository,
            PersonRepository personRepository
    ) {
        this.repository = repository;
        this.appUserRepository = appUserRepository;
        this.residentialUnitRepository = residentialUnitRepository;
        this.personRepository = personRepository;
    }

    @Transactional
    public PackIdResponse create(PackIdCreateRequest req, String actor) {
        PackId p = new PackId();

        p.setTenantId(req.tenantId());
        p.setResidentialUnitId(req.residentialUnitId());
        p.setPersonId(req.personId());
        p.setRegisteredByUserId(req.registeredByUserId());

        p.setPackageType(req.packageType());
        p.setPackageCode(req.packageCode());
        p.setCarrier(req.carrier());
        p.setTrackingCode(req.trackingCode());
        p.setDescription(req.description());

        // se vier null, a entidade já seta "now()" no prePersistHook
        p.setArrivedAt(req.arrivedAt());

        p.setObservations(req.observations());

        p.setCreatedBy(normalizeActor(actor));

        PackId saved = repository.save(p);
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

        if (req.residentialUnitId() != null) p.setResidentialUnitId(req.residentialUnitId());
        if (req.personId() != null) p.setPersonId(req.personId());
        if (req.registeredByUserId() != null) p.setRegisteredByUserId(req.registeredByUserId());

        if (req.packageType() != null) p.setPackageType(req.packageType());
        if (req.packageCode() != null) p.setPackageCode(req.packageCode()); // hash recalcula no preUpdateHook
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
        String packageCode = req.packageCode().trim();

        // 1) unidade residencial pelo "code" (apartment) dentro do tenant
        List<ResidentialUnit> units = residentialUnitRepository
                .findAllByTenantIdAndCodeAndDeletedFalse(tenantId, apartment);

        if (units.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Apartamento não encontrado: " + apartment
            );
        }
        if (units.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Apartamento '" + apartment + "' existe em mais de um condomínio neste tenant. " +
                            "Para ficar 100% determinístico, será preciso informar o condomínio também."
            );
        }

        ResidentialUnit unit = units.get(0);

        // 2) personId: usa o personId do appUser (caminho mais enxuto)
        UUID personId = appUser.getPersonId();
        if (personId == null) {
            // fallback opcional: tenta achar person pelo email
            if (email != null && !email.isBlank()) {
                Person p = personRepository.findByTenantIdAndEmailAndDeletedFalse(tenantId, email)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "AppUser não tem person_id e não achei Person pelo email. Cadastre o vínculo (app_user.person_id)."
                        ));
                personId = p.getId();
            } else {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "AppUser não tem person_id. Cadastre o vínculo (app_user.person_id)."
                );
            }
        }

        // 3) cria o packId
        PackId p = new PackId();
        p.setTenantId(tenantId);
        p.setResidentialUnitId(unit.getId());
        p.setPersonId(personId);

        // quem registrou (opcional na tabela, mas útil)
        p.setRegisteredByUserId(appUser.getId());

        // default simples
        p.setPackageType(PackageType.PACKAGE);
        p.setPackageCode(packageCode);
        p.setLabelPackageCode(packageCode);

        // createdBy: usa email (se não tiver, cai no "system" do AuditableEntity)
        if (email != null && !email.isBlank()) {
            p.setCreatedBy(email);
        }

        try {
            PackId saved = repository.save(p);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Não foi possível salvar o pacote. Verifique apartamento/usuário/person vinculados ao tenant."
            );
        }
    }

    private AppUser resolveAppUser(String email, String subject) {
        // 1) tenta provider+subject
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

        // 2) fallback: email
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
                        r.getApartment(),
                        r.getPackageCode(),
                        r.getLabelPackageCode(),
                        r.getArrivedAt(),
                        r.getCreatedBy()
                ))
                .toList();
    }


}
