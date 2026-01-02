package com.packid.api.service;

import com.packid.api.controller.packid.dto.PackIdCreateRequest;
import com.packid.api.controller.packid.dto.PackIdResponse;
import com.packid.api.controller.packid.dto.PackIdUpdateRequest;
import com.packid.api.domain.model.PackId;
import com.packid.api.domain.repository.PackIdRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PackIdService {

    private final PackIdRepository repository;

    public PackIdService(PackIdRepository repository) {
        this.repository = repository;
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
}
