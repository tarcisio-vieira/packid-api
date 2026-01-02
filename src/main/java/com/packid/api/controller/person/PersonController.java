package com.packid.api.controller.person;

import com.packid.api.controller.person.dto.PersonCreateRequest;
import com.packid.api.controller.person.dto.PersonResponse;
import com.packid.api.controller.person.dto.PersonUpdateRequest;
import com.packid.api.service.PersonService;
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
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonService service;

    public PersonController(PersonService service) {
        this.service = service;
    }

    // GET /api/persons?tenantId=...
    @GetMapping
    public ResponseEntity<List<PersonResponse>> getAll(@RequestParam @NotNull UUID tenantId) {
        return ResponseEntity.ok(service.getAll(tenantId));
    }

    // GET /api/persons/{id}?tenantId=...
    @GetMapping("/{id}")
    public ResponseEntity<PersonResponse> getById(@RequestParam @NotNull UUID tenantId,
                                                  @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(tenantId, id));
    }

    // POST /api/persons
    @PostMapping
    public ResponseEntity<PersonResponse> create(
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody PersonCreateRequest request
    ) {
        PersonResponse created = service.create(request, actor);
        URI location = URI.create("/api/persons/" + created.id() + "?tenantId=" + created.tenantId());
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/persons/{id}?tenantId=...
    @PutMapping("/{id}")
    public ResponseEntity<PersonResponse> update(
            @RequestParam @NotNull UUID tenantId,
            @PathVariable UUID id,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody PersonUpdateRequest request
    ) {
        return ResponseEntity.ok(service.update(tenantId, id, request, actor));
    }

    // DELETE (lógico) /api/persons/{id}?tenantId=... -> 204
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
