package com.packid.api.service;

import com.packid.api.controller.residentialUnit.dto.ResidentialUnitCreateRequest;
import com.packid.api.controller.residentialUnit.dto.ResidentialUnitResponse;
import com.packid.api.controller.residentialUnit.dto.ResidentialUnitUpdateRequest;
import com.packid.api.domain.model.ResidentialUnit;
import com.packid.api.domain.repository.ResidentialUnitRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ResidentialUnitService {

    private final ResidentialUnitRepository repository;

    public ResidentialUnitService(ResidentialUnitRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ResidentialUnitResponse create(ResidentialUnitCreateRequest req, String actor) {
        // unique (condominium_id, code) considerando deleted=false
        repository.findByCondominiumIdAndCodeAndDeletedFalse(req.condominiumId(), req.code())
                .ifPresent(u -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe unidade com este código neste condomínio");
                });

        ResidentialUnit ru = new ResidentialUnit();
        ru.setTenantId(req.tenantId());
        ru.setCondominiumId(req.condominiumId());
        ru.setCode(req.code());
        ru.setName(req.name());
        ru.setActive(req.active() != null ? req.active() : Boolean.TRUE);

        ru.setCreatedBy(normalizeActor(actor));

        ResidentialUnit saved = repository.save(ru);
        return toResponse(saved);
    }

    public ResidentialUnitResponse getById(UUID tenantId, UUID id) {
        ResidentialUnit ru = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ResidentialUnit não encontrada"));
        return toResponse(ru);
    }

    public List<ResidentialUnitResponse> getAll(UUID tenantId) {
        return repository.findAllByTenantIdAndDeletedFalse(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ResidentialUnitResponse update(UUID tenantId, UUID id, ResidentialUnitUpdateRequest req, String actor) {
        ResidentialUnit ru = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ResidentialUnit não encontrada"));

        UUID newCondominiumId = req.condominiumId() != null ? req.condominiumId() : ru.getCondominiumId();
        String newCode = req.code() != null ? req.code() : ru.getCode();

        boolean mudouChaveUnica =
                (req.condominiumId() != null && !req.condominiumId().equals(ru.getCondominiumId())) ||
                        (req.code() != null && !req.code().equalsIgnoreCase(ru.getCode()));

        if (mudouChaveUnica) {
            repository.findByCondominiumIdAndCodeAndDeletedFalse(newCondominiumId, newCode)
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe unidade com este código neste condomínio");
                        }
                    });
            ru.setCondominiumId(newCondominiumId);
            ru.setCode(newCode);
        }

        if (req.name() != null) ru.setName(req.name());
        if (req.active() != null) ru.setActive(req.active());

        ru.setUpdatedBy(normalizeActor(actor));

        ResidentialUnit saved = repository.save(ru);
        return toResponse(saved);
    }

    @Transactional
    public void logicalDelete(UUID tenantId, UUID id, String actor) {
        ResidentialUnit ru = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ResidentialUnit não encontrada"));

        ru.setDeleted(true);
        ru.setDeletedAt(LocalDateTime.now());
        ru.setDeletedBy(normalizeActor(actor));

        repository.save(ru);
    }

    private ResidentialUnitResponse toResponse(ResidentialUnit ru) {
        return new ResidentialUnitResponse(
                ru.getId(),
                ru.getTenantId(),
                ru.getCondominiumId(),
                ru.getCode(),
                ru.getName(),
                ru.getActive(),
                ru.getCreatedAt(),
                ru.getUpdatedAt()
        );
    }

    private String normalizeActor(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor.trim();
    }
}
