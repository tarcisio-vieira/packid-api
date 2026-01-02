package com.packid.api.service;

import com.packid.api.controller.appUser.dto.AppUserCreateRequest;
import com.packid.api.controller.appUser.dto.AppUserResponse;
import com.packid.api.controller.appUser.dto.AppUserUpdateRequest;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.AppUser.AuthProvider;
import com.packid.api.domain.repository.AppUserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AppUserService {

    private final AppUserRepository repository;

    public AppUserService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AppUserResponse create(UUID tenantId, AppUserCreateRequest req, String actor) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId é obrigatório");
        }

        // email único por tenant (considerando deleted=false)
        repository.findByTenantIdAndEmailAndDeletedFalse(tenantId, req.email())
                .ifPresent(u -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe usuário com este email neste tenant");
                });

        AuthProvider provider = (req.provider() != null) ? req.provider() : AuthProvider.GOOGLE;

        // (tenant, provider, subject) único
        repository.findByTenantIdAndProviderAndProviderSubjectAndDeletedFalse(tenantId, provider, req.providerSubject())
                .ifPresent(u -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe usuário com este provider/subject neste tenant");
                });

        AppUser u = new AppUser();
        u.setTenantId(tenantId);
        u.setPersonId(req.personId());
        u.setEmail(req.email());
        u.setFullName(req.fullName());
        u.setProvider(provider);
        u.setProviderSubject(req.providerSubject());
        u.setRole(req.role());
        u.setEnabled(req.enabled() != null ? req.enabled() : Boolean.TRUE);

        u.setCreatedBy(normalizeActor(actor));

        AppUser saved = repository.save(u);
        return toResponse(saved);
    }

    public AppUserResponse getById(UUID tenantId, UUID id) {
        AppUser u = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AppUser não encontrado"));
        return toResponse(u);
    }

    public List<AppUserResponse> getAll(UUID tenantId) {
        return repository.findAllByTenantIdAndDeletedFalse(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AppUserResponse update(UUID tenantId, UUID id, AppUserUpdateRequest req, String actor) {
        AppUser u = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AppUser não encontrado"));

        // email (se mudar, valida conflito)
        if (req.email() != null && !req.email().equalsIgnoreCase(u.getEmail())) {
            repository.findByTenantIdAndEmailAndDeletedFalse(tenantId, req.email())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe usuário com este email neste tenant");
                        }
                    });
            u.setEmail(req.email());
        }

        if (req.fullName() != null) u.setFullName(req.fullName());
        if (req.personId() != null) u.setPersonId(req.personId());
        if (req.role() != null) u.setRole(req.role());
        if (req.enabled() != null) u.setEnabled(req.enabled());

        // provider/providerSubject (se você permitir alterar)
        AuthProvider newProvider = req.provider() != null ? req.provider() : u.getProvider();
        String newSubject = req.providerSubject() != null ? req.providerSubject() : u.getProviderSubject();

        boolean mudouProviderOuSubject =
                newProvider != u.getProvider() || (newSubject != null && !newSubject.equals(u.getProviderSubject()));

        if (mudouProviderOuSubject) {
            repository.findByTenantIdAndProviderAndProviderSubjectAndDeletedFalse(tenantId, newProvider, newSubject)
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe usuário com este provider/subject neste tenant");
                        }
                    });

            u.setProvider(newProvider);
            u.setProviderSubject(newSubject);
        }

        u.setUpdatedBy(normalizeActor(actor));

        AppUser saved = repository.save(u);
        return toResponse(saved);
    }

    @Transactional
    public void logicalDelete(UUID tenantId, UUID id, String actor) {
        AppUser u = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AppUser não encontrado"));

        u.setDeleted(true);
        u.setDeletedAt(LocalDateTime.now());
        u.setDeletedBy(normalizeActor(actor));

        repository.save(u);
    }

    private AppUserResponse toResponse(AppUser u) {
        return new AppUserResponse(
                u.getId(),
                u.getTenantId(),
                u.getPersonId(),
                u.getEmail(),
                u.getFullName(),
                u.getProvider(),
                u.getProviderSubject(),
                u.getRole(),
                u.getEnabled(),
                u.getLastLoginAt(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }

    private String normalizeActor(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor.trim();
    }
}
