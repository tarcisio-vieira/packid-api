package com.packid.api.domain.repository;

import com.packid.api.domain.model.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, UUID> {

    Optional<UnitOfMeasure> findByTenantIdAndCode(UUID tenantId, String code);
    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<UnitOfMeasure> findAllByTenantId(UUID tenantId);

    List<UnitOfMeasure> findAllByTenantIdAndNameContainingIgnoreCase(UUID tenantId, String name);
    List<UnitOfMeasure> findAllByTenantIdAndCodeContainingIgnoreCase(UUID tenantId, String code);

    // >>> soft delete
    Optional<UnitOfMeasure> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    List<UnitOfMeasure> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    Optional<UnitOfMeasure> findByTenantIdAndCodeAndDeletedFalse(UUID tenantId, String code);
}
