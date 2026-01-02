package com.packid.api.controller.residentialUnit;

import com.packid.api.controller.residentialUnit.dto.ResidentialUnitCreateRequest;
import com.packid.api.controller.residentialUnit.dto.ResidentialUnitResponse;
import com.packid.api.controller.residentialUnit.dto.ResidentialUnitUpdateRequest;
import com.packid.api.service.ResidentialUnitService;
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
@RequestMapping("/api/residential-units")
public class ResidentialUnitController {

    private final ResidentialUnitService service;

    public ResidentialUnitController(ResidentialUnitService service) {
        this.service = service;
    }

    // GET /api/residential-units?tenantId=...
    @GetMapping
    public ResponseEntity<List<ResidentialUnitResponse>> getAll(@RequestParam @NotNull UUID tenantId) {
        return ResponseEntity.ok(service.getAll(tenantId));
    }

    // GET /api/residential-units/{id}?tenantId=...
    @GetMapping("/{id}")
    public ResponseEntity<ResidentialUnitResponse> getById(@RequestParam @NotNull UUID tenantId,
                                                           @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(tenantId, id));
    }

    // POST /api/residential-units
    @PostMapping
    public ResponseEntity<ResidentialUnitResponse> create(
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody ResidentialUnitCreateRequest request
    ) {
        ResidentialUnitResponse created = service.create(request, actor);
        URI location = URI.create("/api/residential-units/" + created.id() + "?tenantId=" + created.tenantId());
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/residential-units/{id}?tenantId=...
    @PutMapping("/{id}")
    public ResponseEntity<ResidentialUnitResponse> update(
            @RequestParam @NotNull UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody ResidentialUnitUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(tenantId, id, request, actor));
    }

    // DELETE (lógico) /api/residential-units/{id}?tenantId=... -> 204
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
