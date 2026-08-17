package com.packid.api.controller.condominium;

import com.packid.api.controller.condominium.dto.CondominiumCreateRequest;
import com.packid.api.controller.condominium.dto.CondominiumResponse;
import com.packid.api.controller.condominium.dto.CondominiumUpdateRequest;
import com.packid.api.domain.model.AppUser;
import com.packid.api.service.AccessControlService;
import com.packid.api.service.AuthenticatedUserService;
import com.packid.api.service.CondominiumService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/condominiums")
public class CondominiumController {

    private final CondominiumService service;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;

    public CondominiumController(
            CondominiumService service,
            AuthenticatedUserService authenticatedUserService,
            AccessControlService accessControlService
    ) {
        this.service = service;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
    }

    @GetMapping
    public ResponseEntity<List<CondominiumResponse>> getAll(
            @AuthenticationPrincipal OidcUser principal,
            @RequestParam(required = false) UUID tenantId
    ) {
        AppUser user = operationalUser(principal);
        requireCurrentTenant(user, tenantId);
        return ResponseEntity.ok(service.getAll(user.getTenantId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CondominiumResponse> getById(
            @AuthenticationPrincipal OidcUser principal,
            @RequestParam(required = false) UUID tenantId,
            @PathVariable UUID id
    ) {
        AppUser user = operationalUser(principal);
        requireCurrentTenant(user, tenantId);
        return ResponseEntity.ok(service.getById(user.getTenantId(), id));
    }

    @PostMapping
    public ResponseEntity<CondominiumResponse> create(
            @AuthenticationPrincipal OidcUser principal,
            @Valid @RequestBody CondominiumCreateRequest request
    ) {
        AppUser user = settingsManager(principal);
        requireCurrentTenant(user, request.tenantId());
        CondominiumResponse created = service.create(request, user.getEmail());
        URI location = URI.create("/api/condominiums/" + created.id() + "?tenantId=" + created.tenantId());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CondominiumResponse> update(
            @AuthenticationPrincipal OidcUser principal,
            @RequestParam(required = false) UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody CondominiumUpdateRequest request
    ) {
        AppUser user = settingsManager(principal);
        requireCurrentTenant(user, tenantId);
        return ResponseEntity.ok(service.update(user.getTenantId(), id, request, user.getEmail()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(
            @AuthenticationPrincipal OidcUser principal,
            @RequestParam(required = false) UUID tenantId,
            @PathVariable UUID id
    ) {
        AppUser user = settingsManager(principal);
        requireCurrentTenant(user, tenantId);
        service.logicalDelete(user.getTenantId(), id, user.getEmail());
        return ResponseEntity.noContent().build();
    }

    private AppUser operationalUser(OidcUser principal) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requireOperationalUser(user);
        return user;
    }

    private AppUser settingsManager(OidcUser principal) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requireSettingsManager(user);
        return user;
    }

    private void requireCurrentTenant(AppUser user, UUID requestedTenantId) {
        if (requestedTenantId != null && !user.getTenantId().equals(requestedTenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso a outro condomínio não é permitido.");
        }
    }
}
