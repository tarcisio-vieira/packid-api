package com.packid.api.controller.packid;

import com.packid.api.controller.packid.dto.PackIdRecentResponse;
import com.packid.api.controller.packid.dto.PackIdCreateRequest;
import com.packid.api.controller.packid.dto.PackIdResponse;
import com.packid.api.controller.packid.dto.PackIdUpdateRequest;
import com.packid.api.service.PackIdService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.packid.api.controller.packid.dto.PackIdLabelCreateRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/pack-ids")
public class PackIdController {

    private final PackIdService service;

    public PackIdController(PackIdService service) {
        this.service = service;
    }

    // GET /api/pack-ids?tenantId=...
    @GetMapping
    public ResponseEntity<List<PackIdResponse>> getAll(@RequestParam @NotNull UUID tenantId) {
        return ResponseEntity.ok(service.getAll(tenantId));
    }

    // GET /api/pack-ids/{id}?tenantId=...
    @GetMapping("/{id}")
    public ResponseEntity<PackIdResponse> getById(@RequestParam @NotNull UUID tenantId,
                                                  @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(tenantId, id));
    }

    // POST /api/pack-ids
    @PostMapping
    public ResponseEntity<PackIdResponse> create(
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody PackIdCreateRequest request
    ) {
        PackIdResponse created = service.create(request, actor);
        URI location = URI.create("/api/pack-ids/" + created.id() + "?tenantId=" + created.tenantId());
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/pack-ids/{id}?tenantId=...
    @PutMapping("/{id}")
    public ResponseEntity<PackIdResponse> update(
            @RequestParam @NotNull UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody PackIdUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(tenantId, id, request, actor));
    }

    // DELETE (lógico) /api/pack-ids/{id}?tenantId=... -> 204
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(
            @RequestParam @NotNull UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        service.logicalDelete(tenantId, id, actor);
        return ResponseEntity.noContent().build();
    }

    // POST /api/pack-ids/from-label  (usa OIDC para descobrir tenant/user/person)
    @PostMapping("/from-label")
    public ResponseEntity<PackIdResponse> createFromLabel(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody PackIdLabelCreateRequest request
    ) {
        PackIdResponse created = service.createFromLabel(user, request);

        URI location = URI.create("/api/pack-ids/" + created.id() + "?tenantId=" + created.tenantId());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<PackIdRecentResponse>> recent(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return ResponseEntity.ok(service.getRecentForMe(user, limit, from, to));
    }
}
