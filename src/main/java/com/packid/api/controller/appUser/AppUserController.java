package com.packid.api.controller.appUser;

import com.packid.api.controller.appUser.dto.AppUserCreateRequest;
import com.packid.api.controller.appUser.dto.AppUserResponse;
import com.packid.api.controller.appUser.dto.AppUserUpdateRequest;
import com.packid.api.domain.model.AppUser;
import com.packid.api.service.AccessControlService;
import com.packid.api.service.AppUserService;
import com.packid.api.service.AuthenticatedUserService;
import com.packid.api.domain.repository.TenantRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/app-users")
public class AppUserController {

    private final AppUserService service;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final TenantRepository tenantRepository;

    public AppUserController(AppUserService service, AuthenticatedUserService authenticatedUserService,
                             AccessControlService accessControlService, TenantRepository tenantRepository) {
        this.service = service;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.tenantRepository = tenantRepository;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        AppUser appUser = authenticatedUserService.requireAppUser(user);
        String displayName = user.getFullName();
        if (displayName == null || displayName.isBlank()) displayName = appUser.getFullName();
        if (displayName == null || displayName.isBlank()) displayName = appUser.getEmail();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("name", displayName);
        response.put("email", appUser.getEmail());
        response.put("role", appUser.getRole());
        String tenantName = tenantRepository.findByIdAndDeletedFalse(appUser.getTenantId())
                .map(com.packid.api.domain.model.Tenant::getName)
                .filter(name -> !name.isBlank())
                .orElse("Condomínio");
        response.put("tenantName", tenantName);
        response.put("canManageSettings", accessControlService.canManageSettings(appUser));
        response.put("canManageProtectedRegistry", accessControlService.canManageProtectedRegistry(appUser));
        response.put("canOperateCondominium", accessControlService.canOperateCondominium(appUser));
        return response;
    }

    @GetMapping
    public ResponseEntity<List<AppUserResponse>> getAll(@AuthenticationPrincipal OidcUser user) {
        AppUser manager = authenticatedUserService.requireAppUser(user);
        return ResponseEntity.ok(service.getAll(manager));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AppUserResponse> getById(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        AppUser manager = authenticatedUserService.requireAppUser(user);
        return ResponseEntity.ok(service.getById(manager, id));
    }

    @PostMapping
    public ResponseEntity<AppUserResponse> create(@AuthenticationPrincipal OidcUser user,
                                                   @Valid @RequestBody AppUserCreateRequest request) {
        AppUser manager = authenticatedUserService.requireAppUser(user);
        AppUserResponse created = service.create(manager, request);
        return ResponseEntity.created(URI.create("/api/app-users/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppUserResponse> update(@AuthenticationPrincipal OidcUser user,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody AppUserUpdateRequest request) {
        AppUser manager = authenticatedUserService.requireAppUser(user);
        return ResponseEntity.ok(service.update(manager, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        AppUser manager = authenticatedUserService.requireAppUser(user);
        service.logicalDelete(manager, id);
        return ResponseEntity.noContent().build();
    }
}
