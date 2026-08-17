package com.packid.api.controller.tenant;

import com.packid.api.controller.tenant.dto.TenantCreateRequest;
import com.packid.api.controller.tenant.dto.TenantResponse;
import com.packid.api.controller.tenant.dto.TenantUpdateRequest;
import com.packid.api.domain.model.AppUser;
import com.packid.api.service.AccessControlService;
import com.packid.api.service.AuthenticatedUserService;
import com.packid.api.service.TenantService;
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
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService service;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;

    public TenantController(
            TenantService service,
            AuthenticatedUserService authenticatedUserService,
            AccessControlService accessControlService
    ) {
        this.service = service;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
    }

    // Usuários do condomínio só podem enxergar o próprio tenant.
    @GetMapping
    public ResponseEntity<List<TenantResponse>> getAll(@AuthenticationPrincipal OidcUser principal) {
        AppUser user = operationalUser(principal);
        return ResponseEntity.ok(List.of(service.getById(user.getTenantId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getById(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable @NotNull UUID id
    ) {
        AppUser user = operationalUser(principal);
        requireCurrentTenant(user, id);
        return ResponseEntity.ok(service.getById(id));
    }

    // Criação/alteração estrutural do tenant fica somente para o ADMIN técnico.
    @PostMapping
    public ResponseEntity<TenantResponse> create(
            @AuthenticationPrincipal OidcUser principal,
            @Valid @RequestBody TenantCreateRequest request
    ) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requireAdmin(user);
        TenantResponse created = service.create(request, user.getEmail());
        URI location = URI.create("/api/tenants/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> update(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable @NotNull UUID id,
            @Valid @RequestBody TenantUpdateRequest request
    ) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requireAdmin(user);
        requireCurrentTenant(user, id);
        return ResponseEntity.ok(service.update(id, request, user.getEmail()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(
            @AuthenticationPrincipal OidcUser principal,
            @PathVariable @NotNull UUID id
    ) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requireAdmin(user);
        requireCurrentTenant(user, id);
        service.logicalDelete(id, user.getEmail());
        return ResponseEntity.noContent().build();
    }

    private AppUser operationalUser(OidcUser principal) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requireOperationalUser(user);
        return user;
    }

    private void requireCurrentTenant(AppUser user, UUID tenantId) {
        if (tenantId == null || !tenantId.equals(user.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso a outro condomínio não é permitido.");
        }
    }
}
