package com.packid.api.domain.repository;

import com.packid.api.domain.model.Condominium;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CondominiumRepository extends JpaRepository<Condominium, UUID> {

    Optional<Condominium> findByTenantIdAndName(UUID tenantId, String name);
    boolean existsByTenantIdAndName(UUID tenantId, String name);
    List<Condominium> findAllByTenantId(UUID tenantId);

    // >>> soft delete + segurança multi-tenant
    Optional<Condominium> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    List<Condominium> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    Optional<Condominium> findByTenantIdAndNameAndDeletedFalse(UUID tenantId, String name);
}
