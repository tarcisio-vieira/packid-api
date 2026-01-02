package com.packid.api.domain.repository;

import com.packid.api.domain.model.ResidentialUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResidentialUnitRepository extends JpaRepository<ResidentialUnit, UUID> {

    Optional<ResidentialUnit> findByCondominiumIdAndCode(UUID condominiumId, String code);
    boolean existsByCondominiumIdAndCode(UUID condominiumId, String code);

    List<ResidentialUnit> findAllByTenantId(UUID tenantId);
    List<ResidentialUnit> findAllByCondominiumId(UUID condominiumId);
    List<ResidentialUnit> findAllByCondominiumIdAndActiveTrue(UUID condominiumId);

    // >>> soft delete + escopo
    Optional<ResidentialUnit> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    List<ResidentialUnit> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    Optional<ResidentialUnit> findByCondominiumIdAndCodeAndDeletedFalse(UUID condominiumId, String code);

    List<ResidentialUnit> findAllByCondominiumIdAndDeletedFalse(UUID condominiumId);
    List<ResidentialUnit> findAllByCondominiumIdAndActiveTrueAndDeletedFalse(UUID condominiumId);
}
