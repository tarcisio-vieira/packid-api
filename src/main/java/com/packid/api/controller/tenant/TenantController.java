package com.packid.api.controller.tenant;

import com.packid.api.controller.tenant.dto.TenantCreateRequest;
import com.packid.api.controller.tenant.dto.TenantResponse;
import com.packid.api.controller.tenant.dto.TenantUpdateRequest;
import com.packid.api.service.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    // GET /api/tenants
    @GetMapping
    public ResponseEntity<List<TenantResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    // GET /api/tenants/{id}
    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getById(@PathVariable @NotNull UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    // POST /api/tenants
    @PostMapping
    public ResponseEntity<TenantResponse> create(
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody TenantCreateRequest request
    ) {
        TenantResponse created = service.create(request, actor);
        URI location = URI.create("/api/tenants/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/tenants/{id}
    @PutMapping("/{id}")
    public ResponseEntity<TenantResponse> update(
            @PathVariable @NotNull UUID id,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody TenantUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(id, request, actor));
    }

    // DELETE (lógico) /api/tenants/{id} -> 204
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(
            @PathVariable @NotNull UUID id,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        service.logicalDelete(id, actor);
        return ResponseEntity.noContent().build();
    }
}
