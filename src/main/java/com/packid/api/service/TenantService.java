package com.packid.api.service;

import com.packid.api.controller.tenant.dto.TenantCreateRequest;
import com.packid.api.controller.tenant.dto.TenantResponse;
import com.packid.api.controller.tenant.dto.TenantUpdateRequest;
import com.packid.api.domain.model.Tenant;
import com.packid.api.domain.repository.TenantRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository repository;

    public TenantService(TenantRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TenantResponse create(TenantCreateRequest req, String actor) {
        // slug é unique (mas considerar soft delete)
        repository.findBySlugAndDeletedFalse(req.slug())
                .ifPresent(t -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe tenant com este slug");
                });

        Tenant t = new Tenant();
        t.setName(req.name());
        t.setSlug(req.slug());
        t.setActive(req.active() != null ? req.active() : Boolean.TRUE);

        t.setCreatedBy(normalizeActor(actor));

        Tenant saved = repository.save(t);
        return toResponse(saved);
    }

    public TenantResponse getById(UUID id) {
        Tenant t = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant não encontrado"));
        return toResponse(t);
    }

    public List<TenantResponse> getAll() {
        return repository.findAllByDeletedFalse()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TenantResponse update(UUID id, TenantUpdateRequest req, String actor) {
        Tenant t = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant não encontrado"));

        if (req.slug() != null && !req.slug().equalsIgnoreCase(t.getSlug())) {
            repository.findBySlugAndDeletedFalse(req.slug())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe tenant com este slug");
                        }
                    });
            t.setSlug(req.slug());
        }

        if (req.name() != null) t.setName(req.name());
        if (req.active() != null) t.setActive(req.active());

        t.setUpdatedBy(normalizeActor(actor));

        Tenant saved = repository.save(t);
        return toResponse(saved);
    }

    @Transactional
    public void logicalDelete(UUID id, String actor) {
        Tenant t = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant não encontrado"));

        t.setDeleted(true);
        t.setDeletedAt(LocalDateTime.now());
        t.setDeletedBy(normalizeActor(actor));

        repository.save(t);
    }

    private TenantResponse toResponse(Tenant t) {
        return new TenantResponse(
                t.getId(),
                t.getName(),
                t.getSlug(),
                t.getActive(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    private String normalizeActor(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor.trim();
    }
}
