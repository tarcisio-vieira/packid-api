package com.packid.api.controller.unitOfMeasure;

import com.packid.api.controller.unitOfMeasure.dto.UnitOfMeasureCreateRequest;
import com.packid.api.controller.unitOfMeasure.dto.UnitOfMeasureResponse;
import com.packid.api.controller.unitOfMeasure.dto.UnitOfMeasureUpdateRequest;
import com.packid.api.service.UnitOfMeasureService;
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
@RequestMapping("/api/unit-of-measures")
public class UnitOfMeasureController {

    private final UnitOfMeasureService service;

    public UnitOfMeasureController(UnitOfMeasureService service) {
        this.service = service;
    }

    // GET /api/unit-of-measures?tenantId=...
    @GetMapping
    public ResponseEntity<List<UnitOfMeasureResponse>> getAll(@RequestParam @NotNull UUID tenantId) {
        return ResponseEntity.ok(service.getAll(tenantId));
    }

    // GET /api/unit-of-measures/{id}?tenantId=...
    @GetMapping("/{id}")
    public ResponseEntity<UnitOfMeasureResponse> getById(@RequestParam @NotNull UUID tenantId,
                                                         @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(tenantId, id));
    }

    // POST /api/unit-of-measures?tenantId=...
    @PostMapping
    public ResponseEntity<UnitOfMeasureResponse> create(@RequestParam @NotNull UUID tenantId,
                                                        @RequestHeader(value = "X-Actor", required = false) String actor,
                                                        @Valid @RequestBody UnitOfMeasureCreateRequest request) {

        // garante tenantId vindo por query (mesmo se vier no body)
        UnitOfMeasureCreateRequest req = new UnitOfMeasureCreateRequest(
                tenantId,
                request.code(),
                request.name(),
                request.description(),
                request.symbol()
        );

        UnitOfMeasureResponse created = service.create(req, actor);

        URI location = URI.create("/api/unit-of-measures/" + created.id() + "?tenantId=" + tenantId);
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/unit-of-measures/{id}?tenantId=...
    @PutMapping("/{id}")
    public ResponseEntity<UnitOfMeasureResponse> update(@RequestParam @NotNull UUID tenantId,
                                                        @PathVariable UUID id,
                                                        @RequestHeader(value = "X-Actor", required = false) String actor,
                                                        @Valid @RequestBody UnitOfMeasureUpdateRequest request) {
        return ResponseEntity.ok(service.update(tenantId, id, request, actor));
    }

    // DELETE (lógico) /api/unit-of-measures/{id}?tenantId=...
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(@RequestParam @NotNull UUID tenantId,
                                              @PathVariable UUID id,
                                              @RequestHeader(value = "X-Actor", required = false) String actor) {
        service.logicalDelete(tenantId, id, actor);
        return ResponseEntity.noContent().build(); // 204
    }
}
