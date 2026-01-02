package com.packid.api.service;

import com.packid.api.controller.condominium.dto.CondominiumCreateRequest;
import com.packid.api.controller.condominium.dto.CondominiumResponse;
import com.packid.api.controller.condominium.dto.CondominiumUpdateRequest;
import com.packid.api.domain.model.Condominium;
import com.packid.api.domain.repository.CondominiumRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CondominiumService {

    private final CondominiumRepository repository;

    public CondominiumService(CondominiumRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CondominiumResponse create(CondominiumCreateRequest req, String actor) {
        // unique (tenant_id, name) considerando deleted=false
        repository.findByTenantIdAndNameAndDeletedFalse(req.tenantId(), req.name())
                .ifPresent(c -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe condomínio com este nome neste tenant");
                });

        Condominium c = new Condominium();
        c.setTenantId(req.tenantId());
        c.setName(req.name());
        c.setAddressLine1(req.addressLine1());
        c.setAddressLine2(req.addressLine2());
        c.setCity(req.city());
        c.setState(req.state());
        c.setZipCode(req.zipCode());

        c.setCreatedBy(normalizeActor(actor));

        Condominium saved = repository.save(c);
        return toResponse(saved);
    }

    public CondominiumResponse getById(UUID tenantId, UUID id) {
        Condominium c = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        return toResponse(c);
    }

    public List<CondominiumResponse> getAll(UUID tenantId) {
        return repository.findAllByTenantIdAndDeletedFalse(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CondominiumResponse update(UUID tenantId, UUID id, CondominiumUpdateRequest req, String actor) {
        Condominium c = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));

        // name (se mudar, valida unique por tenant)
        if (req.name() != null && !req.name().equalsIgnoreCase(c.getName())) {
            repository.findByTenantIdAndNameAndDeletedFalse(tenantId, req.name())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe condomínio com este nome neste tenant");
                        }
                    });
            c.setName(req.name());
        }

        if (req.addressLine1() != null) c.setAddressLine1(req.addressLine1());
        if (req.addressLine2() != null) c.setAddressLine2(req.addressLine2());
        if (req.city() != null) c.setCity(req.city());
        if (req.state() != null) c.setState(req.state());
        if (req.zipCode() != null) c.setZipCode(req.zipCode());

        c.setUpdatedBy(normalizeActor(actor));

        Condominium saved = repository.save(c);
        return toResponse(saved);
    }

    @Transactional
    public void logicalDelete(UUID tenantId, UUID id, String actor) {
        Condominium c = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));

        c.setDeleted(true);
        c.setDeletedAt(LocalDateTime.now());
        c.setDeletedBy(normalizeActor(actor));

        repository.save(c);
    }

    private CondominiumResponse toResponse(Condominium c) {
        return new CondominiumResponse(
                c.getId(),
                c.getTenantId(),
                c.getName(),
                c.getAddressLine1(),
                c.getAddressLine2(),
                c.getCity(),
                c.getState(),
                c.getZipCode(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private String normalizeActor(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor.trim();
    }
}
