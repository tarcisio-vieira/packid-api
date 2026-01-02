package com.packid.api.domain.repository;

import com.packid.api.domain.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // >>> soft delete
    Optional<Product> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);

    List<Product> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    Optional<Product> findByTenantIdAndCodeAndDeletedFalse(UUID tenantId, String code);
}
