package com.packid.api.controller.product;

import com.packid.api.controller.product.dto.ProductCreateRequest;
import com.packid.api.controller.product.dto.ProductResponse;
import com.packid.api.controller.product.dto.ProductUpdateRequest;
import com.packid.api.service.ProductService;
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
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    // GET /api/products?tenantId=...
    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAll(@RequestParam @NotNull UUID tenantId) {
        return ResponseEntity.ok(service.getAll(tenantId));
    }

    // GET /api/products/{id}?tenantId=...
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@RequestParam @NotNull UUID tenantId,
                                                   @PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(tenantId, id));
    }

    // POST /api/products?tenantId=...
    @PostMapping
    public ResponseEntity<ProductResponse> create(@RequestParam @NotNull UUID tenantId,
                                                  @RequestHeader(value = "X-Actor", required = false) String actor,
                                                  @Valid @RequestBody ProductCreateRequest request) {

        ProductResponse created = service.create(tenantId, request, actor);

        URI location = URI.create("/api/products/" + created.id() + "?tenantId=" + tenantId);
        return ResponseEntity.created(location).body(created);
    }

    // PUT /api/products/{id}?tenantId=...
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@RequestParam @NotNull UUID tenantId,
                                                  @PathVariable UUID id,
                                                  @RequestHeader(value = "X-Actor", required = false) String actor,
                                                  @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(service.update(tenantId, id, request, actor));
    }

    // DELETE (lógico) /api/products/{id}?tenantId=...
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> logicalDelete(@RequestParam @NotNull UUID tenantId,
                                              @PathVariable UUID id,
                                              @RequestHeader(value = "X-Actor", required = false) String actor) {
        service.logicalDelete(tenantId, id, actor);
        return ResponseEntity.noContent().build(); // 204
    }
}
