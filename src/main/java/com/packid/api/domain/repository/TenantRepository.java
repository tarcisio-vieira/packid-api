package com.packid.api.domain.repository;

import com.packid.api.domain.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    // slug é unique
    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    // útil quando só quer tenants ativos
    Optional<Tenant> findBySlugAndActiveTrue(String slug);

    // >>> soft delete
    Optional<Tenant> findByIdAndDeletedFalse(UUID id);
    List<Tenant> findAllByDeletedFalse();

    Optional<Tenant> findBySlugAndDeletedFalse(String slug);
}
