package com.packid.api.service;

import com.packid.api.controller.product.dto.ProductCreateRequest;
import com.packid.api.controller.product.dto.ProductResponse;
import com.packid.api.controller.product.dto.ProductUpdateRequest;
import com.packid.api.domain.model.Product;
import com.packid.api.domain.repository.ProductRepository;
import com.packid.api.domain.repository.UnitOfMeasureRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;

    public ProductService(ProductRepository productRepository,
                          UnitOfMeasureRepository unitOfMeasureRepository) {
        this.productRepository = productRepository;
        this.unitOfMeasureRepository = unitOfMeasureRepository;
    }

    @Transactional
    public ProductResponse create(UUID tenantId, ProductCreateRequest req, String actor) {
        // unique (tenant_id, code) considerando deleted=false
        productRepository.findByTenantIdAndCodeAndDeletedFalse(tenantId, req.code())
                .ifPresent(p -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe Product com este código neste tenant");
                });

        // garante que UoM existe e é do tenant
        unitOfMeasureRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, req.unitOfMeasureId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UnitOfMeasure inválida para este tenant"));

        Product p = new Product();
        p.setTenantId(tenantId);
        p.setCode(req.code());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setUnitPrice(req.unitPrice());
        p.setUnitOfMeasureId(req.unitOfMeasureId());

        p.setCreatedBy(normalizeActor(actor));

        Product saved = productRepository.save(p);
        return toResponse(saved);
    }

    public ProductResponse getById(UUID tenantId, UUID id) {
        Product p = productRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product não encontrado"));
        return toResponse(p);
    }

    public List<ProductResponse> getAll(UUID tenantId) {
        return productRepository.findAllByTenantIdAndDeletedFalse(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductResponse update(UUID tenantId, UUID id, ProductUpdateRequest req, String actor) {
        Product p = productRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product não encontrado"));

        // code (se mudar, valida unique por tenant)
        if (req.code() != null && !req.code().equalsIgnoreCase(p.getCode())) {
            productRepository.findByTenantIdAndCodeAndDeletedFalse(tenantId, req.code())
                    .ifPresent(other -> {
                        if (!other.getId().equals(id)) {
                            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe Product com este código neste tenant");
                        }
                    });
            p.setCode(req.code());
        }

        if (req.name() != null) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        if (req.unitPrice() != null) p.setUnitPrice(req.unitPrice());

        if (req.unitOfMeasureId() != null && !req.unitOfMeasureId().equals(p.getUnitOfMeasureId())) {
            unitOfMeasureRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, req.unitOfMeasureId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "UnitOfMeasure inválida para este tenant"));
            p.setUnitOfMeasureId(req.unitOfMeasureId());
        }

        p.setUpdatedBy(normalizeActor(actor));

        Product saved = productRepository.save(p);
        return toResponse(saved);
    }

    @Transactional
    public void logicalDelete(UUID tenantId, UUID id, String actor) {
        Product p = productRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product não encontrado"));

        p.setDeleted(true);
        p.setDeletedAt(LocalDateTime.now());
        p.setDeletedBy(normalizeActor(actor));

        productRepository.save(p);
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getTenantId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getUnitPrice(),
                p.getUnitOfMeasureId(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    private String normalizeActor(String actor) {
        return (actor == null || actor.isBlank()) ? "system" : actor.trim();
    }
}
