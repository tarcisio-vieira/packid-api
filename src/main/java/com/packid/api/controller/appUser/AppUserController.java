package com.packid.api.controller.appUser;

import com.packid.api.controller.appUser.dto.AppUserCreateRequest;
import com.packid.api.controller.appUser.dto.AppUserResponse;
import com.packid.api.controller.appUser.dto.AppUserUpdateRequest;
import com.packid.api.service.AppUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/app-users")
public class AppUserController {

    private final AppUserService service;

    public AppUserController(AppUserService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        return Map.of(
                "name", user.getFullName(),
                "email", user.getEmail()
        );
    }

    // GET /api/app-users?tenantId=...
    @GetMapping
    public ResponseEntity<List<AppUserResponse>> getAll(@RequestParam @NotNull UUID tenantId) {
        return ResponseEntity.ok(service.getAll(tenantId));
    }

    // GET /api/app-users/{id}?tenantId=...
    @GetMapping("/{id}")
    public ResponseEntity<AppUserResponse> getById(@RequestParam @NotNull UUID tenantId,
                                                   @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(tenantId, id));
    }

    // POST /api/app-users?tenantId=...
    @PostMapping
    public ResponseEntity<AppUserResponse> create(@RequestParam @NotNull UUID tenantId,
                                                  @RequestHeader(value = "X-Actor", required = false) String actor,
                                                  @Valid @RequestBody AppUserCreateRequest request) {

        AppUserResponse created = service.create(tenantId, request, actor);

        URI location = URI.create("/api/app-users/" + created.id() + "?tenantId=" + tenantId);
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/app-users/{id}?tenantId=...
    @PutMapping("/{id}")
    public ResponseEntity<AppUserResponse> update(@RequestParam @NotNull UUID tenantId,
                                                  @PathVariable UUID id,
                                                  @RequestHeader(value = "X-Actor", required = false) String actor,
                                                  @Valid @RequestBody AppUserUpdateRequest request) {
        return ResponseEntity.ok(service.update(tenantId, id, request, actor));
    }

    // DELETE (lógico) /api/app-users/{id}?tenantId=...
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(@RequestParam @NotNull UUID tenantId,
                                              @PathVariable UUID id,
                                              @RequestHeader(value = "X-Actor", required = false) String actor) {
        service.logicalDelete(tenantId, id, actor);
        return ResponseEntity.noContent().build(); // 204
    }
}
