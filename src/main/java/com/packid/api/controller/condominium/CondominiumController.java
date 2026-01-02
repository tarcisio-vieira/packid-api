package com.packid.api.controller.condominium;

import com.packid.api.controller.condominium.dto.CondominiumCreateRequest;
import com.packid.api.controller.condominium.dto.CondominiumResponse;
import com.packid.api.controller.condominium.dto.CondominiumUpdateRequest;
import com.packid.api.service.CondominiumService;
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
@RequestMapping("/api/condominiums")
public class CondominiumController {

    private final CondominiumService service;

    public CondominiumController(CondominiumService service) {
        this.service = service;
    }

    // GET /api/condominiums?tenantId=...
    @GetMapping
    public ResponseEntity<List<CondominiumResponse>> getAll(@RequestParam @NotNull UUID tenantId) {
        return ResponseEntity.ok(service.getAll(tenantId));
    }

    // GET /api/condominiums/{id}?tenantId=...
    @GetMapping("/{id}")
    public ResponseEntity<CondominiumResponse> getById(@RequestParam @NotNull UUID tenantId,
                                                       @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(tenantId, id));
    }

    // POST /api/condominiums
    @PostMapping
    public ResponseEntity<CondominiumResponse> create(
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody CondominiumCreateRequest request
    ) {
        CondominiumResponse created = service.create(request, actor);
        URI location = URI.create("/api/condominiums/" + created.id() + "?tenantId=" + created.tenantId());
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/condominiums/{id}?tenantId=...
    @PutMapping("/{id}")
    public ResponseEntity<CondominiumResponse> update(
            @RequestParam @NotNull UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody CondominiumUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(tenantId, id, request, actor));
    }

    // DELETE (lógico) /api/condominiums/{id}?tenantId=... -> 204
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(
            @RequestParam @NotNull UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Actor", required = false) String actor
    ) {
        service.logicalDelete(tenantId, id, actor);
        return ResponseEntity.noContent().build();
    }
}
