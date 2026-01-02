package com.packid.api.service;

import com.packid.api.controller.unitOfMeasure.dto.UnitOfMeasureCreateRequest;
import com.packid.api.controller.unitOfMeasure.dto.UnitOfMeasureResponse;
import com.packid.api.controller.unitOfMeasure.dto.UnitOfMeasureUpdateRequest;
import com.packid.api.domain.model.UnitOfMeasure;
import com.packid.api.domain.repository.UnitOfMeasureRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository repository;

    public UnitOfMeasureService(UnitOfMeasureRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UnitOfMeasureResponse create(UnitOfMeasureCreateRequest req, String actor) {
        // unique (tenant_id, code) considerando deleted=false
        repository.findByTenantIdAndCodeAndDeletedFalse(req.tenantId(), req.code())
                .ifPresent(u -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe UoM com este código neste tenant");
                });

        UnitOfMeasure u = new UnitOfMeasure();
        u.setTenantId(req.tenantId());
        u.setCode(req.code());
        u.setName(req.name());
        u.setDescription(req.description());
        u.setSymbol(req.symbol());

        u.setCreatedBy(normalizeActor(actor));

        UnitOfMeasure saved = repository.save(u);
        return toResponse(saved);
    }

    public UnitOfMeasureResponse getById(UUID tenantId, UUID id) {
        UnitOfMeasure u = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UnitOfMeasure não encontrada"));
        return toResponse(u);
    }

    public List<UnitOfMeasureResponse> getAll(UUID tenantId) {
        return repository.findAllByTenantIdAndDeletedFalse(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UnitOfMeasureResponse update(UUID tenantId, UUID id, UnitOfMeasureUpdateRequest req, String actor) {
        UnitOfMeasure u = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UnitOfMeasure não encontrada"));

        // code (se mudar, valida unique por tenant)
        if (req.code() != null && !req.code().equalsIgnoreCase(u.getCode())) {
            repository.findByTenantIdAndCodeAndDeletedFalse(tenantId, req.code())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe UoM com este código neste tenant");
                        }
                    });
            u.setCode(req.code());
        }

        if (req.name() != null) u.setName(req.name());
        if (req.description() != null) u.setDescription(req.description());
        if (req.symbol() != null) u.setSymbol(req.symbol());

        u.setUpdatedBy(normalizeActor(actor));

        UnitOfMeasure saved = repository.save(u);
        return toResponse(saved);
    }

    @Transactional
    public void logicalDelete(UUID tenantId, UUID id, String actor) {
        UnitOfMeasure u = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UnitOfMeasure não encontrada"));

        u.setDeleted(true);
        u.setDeletedAt(LocalDateTime.now());
        u.setDeletedBy(normalizeActor(actor));

        repository.save(u);
    }

    private UnitOfMeasureResponse toResponse(UnitOfMeasure u) {
        return new UnitOfMeasureResponse(
                u.getId(),
                u.getTenantId(),
                u.getCode(),
                u.getName(),
                u.getDescription(),
                u.getSymbol(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }

    private String normalizeActor(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor.trim();
    }
}
