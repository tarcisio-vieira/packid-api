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
import java.util.Locale;
import java.util.UUID;

@Service
public class AppUserService {

    private final AppUserRepository repository;
    private final AccessControlService accessControlService;

    public AppUserService(AppUserRepository repository, AccessControlService accessControlService) {
        this.repository = repository;
        this.accessControlService = accessControlService;
    }

    @Transactional
    public AppUserResponse create(AppUser manager, AppUserCreateRequest req) {
        accessControlService.requireSettingsManager(manager);
        UUID tenantId = manager.getTenantId();
        String email = normalizeEmail(req.email());
        String role = accessControlService.normalizeRole(req.role());
        validateAssignableRole(manager, role);

        repository.findByTenantIdAndEmailAndDeletedFalse(tenantId, email).ifPresent(u -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe usuário com este e-mail neste condomínio.");
        });

        AuthProvider provider = req.provider() != null ? req.provider() : AuthProvider.GOOGLE;
        String providerSubject = clean(req.providerSubject());
        if (providerSubject != null) {
            repository.findByTenantIdAndProviderAndProviderSubjectAndDeletedFalse(tenantId, provider, providerSubject)
                    .ifPresent(u -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Esta conta Google já está vinculada a outro usuário."); });
        }

        AppUser u = new AppUser();
        u.setTenantId(tenantId);
        u.setPersonId(req.personId());
        u.setEmail(email);
        u.setFullName(clean(req.fullName()));
        u.setProvider(provider);
        u.setProviderSubject(providerSubject);
        u.setRole(role);
        u.setEnabled(req.enabled() == null ? Boolean.TRUE : req.enabled());
        u.setCreatedBy(actor(manager));
        return toResponse(repository.save(u));
    }

    public AppUserResponse getById(AppUser manager, UUID id) {
        accessControlService.requireSettingsManager(manager);
        return toResponse(requireInTenant(manager.getTenantId(), id));
    }

    public List<AppUserResponse> getAll(AppUser manager) {
        accessControlService.requireSettingsManager(manager);
        return repository.findAllByTenantIdAndDeletedFalse(manager.getTenantId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AppUserResponse update(AppUser manager, UUID id, AppUserUpdateRequest req) {
        accessControlService.requireSettingsManager(manager);
        AppUser u = requireInTenant(manager.getTenantId(), id);

        String requestedRole = req.role() == null ? u.getRole() : accessControlService.normalizeRole(req.role());
        validateAssignableRole(manager, requestedRole);
        if (!accessControlService.isAdmin(manager) && "ADMIN".equalsIgnoreCase(u.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A secretaria não pode alterar um usuário administrador.");
        }

        if (req.email() != null) {
            String email = normalizeEmail(req.email());
            if (!email.equalsIgnoreCase(u.getEmail())) {
                repository.findByTenantIdAndEmailAndDeletedFalse(manager.getTenantId(), email).ifPresent(other -> {
                    if (!other.getId().equals(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Já existe usuário com este e-mail neste condomínio.");
                });
                u.setEmail(email);
            }
        }
        if (req.fullName() != null) u.setFullName(clean(req.fullName()));
        if (req.personId() != null) u.setPersonId(req.personId());
        if (req.role() != null) u.setRole(requestedRole);
        if (req.enabled() != null) u.setEnabled(req.enabled());

        AuthProvider newProvider = req.provider() != null ? req.provider() : u.getProvider();
        String newSubject = req.providerSubject() != null ? clean(req.providerSubject()) : u.getProviderSubject();
        boolean changedProvider = newProvider != u.getProvider();
        boolean changedSubject = !java.util.Objects.equals(clean(newSubject), clean(u.getProviderSubject()));
        if ((changedProvider || changedSubject) && newSubject != null) {
            repository.findByTenantIdAndProviderAndProviderSubjectAndDeletedFalse(manager.getTenantId(), newProvider, newSubject)
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Esta conta Google já está vinculada a outro usuário.");
                    });
        }
        u.setProvider(newProvider);
        u.setProviderSubject(newSubject);
        u.setUpdatedBy(actor(manager));
        return toResponse(repository.save(u));
    }

    @Transactional
    public void logicalDelete(AppUser manager, UUID id) {
        accessControlService.requireSettingsManager(manager);
        AppUser u = requireInTenant(manager.getTenantId(), id);
        if (u.getId().equals(manager.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não é possível excluir o próprio usuário autenticado.");
        }
        if (!accessControlService.isAdmin(manager) && "ADMIN".equalsIgnoreCase(u.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A secretaria não pode excluir um usuário administrador.");
        }
        u.setDeleted(true);
        u.setDeletedAt(LocalDateTime.now());
        u.setDeletedBy(actor(manager));
        repository.save(u);
    }

    private void validateAssignableRole(AppUser manager, String role) {
        if (!accessControlService.isAdmin(manager) && AccessControlService.ROLE_ADMIN.equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente administrador pode atribuir o perfil ADMIN.");
        }
    }

    private AppUser requireInTenant(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    private AppUserResponse toResponse(AppUser u) {
        return new AppUserResponse(u.getId(), u.getTenantId(), u.getPersonId(), u.getEmail(), u.getFullName(),
                u.getProvider(), u.getProviderSubject(), u.getRole(), u.getEnabled(), u.getLastLoginAt(),
                u.getCreatedAt(), u.getUpdatedAt());
    }

    private String actor(AppUser user) {
        return clean(user.getEmail()) == null ? "system" : user.getEmail().trim();
    }

    private String normalizeEmail(String value) {
        String email = clean(value);
        if (email == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail é obrigatório.");
        return email.toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
