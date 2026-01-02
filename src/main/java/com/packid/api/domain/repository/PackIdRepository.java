package com.packid.api.domain.repository;

import com.packid.api.domain.model.PackId;
import com.packid.api.domain.type.PackageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PackIdRepository extends JpaRepository<PackId, UUID> {

    // Escopo por tenant (recomendado em apps multi-tenant)
    Optional<PackId> findByTenantIdAndId(UUID tenantId, UUID id);

    List<PackId> findAllByTenantId(UUID tenantId);

    // Filtros comuns
    List<PackId> findAllByTenantIdAndResidentialUnitId(UUID tenantId, UUID residentialUnitId);

    List<PackId> findAllByTenantIdAndPersonId(UUID tenantId, UUID personId);

    List<PackId> findAllByTenantIdAndPackageType(UUID tenantId, PackageType packageType);

    // Busca pelo hash do código do pacote (você calcula no prePersist/preUpdate)
    Optional<PackId> findByTenantIdAndPackageCodeHash(UUID tenantId, String packageCodeHash);

    // “Pendentes” (ainda não entregues)
    List<PackId> findAllByTenantIdAndHandedOverAtIsNull(UUID tenantId);

    // Por período de chegada
    List<PackId> findAllByTenantIdAndArrivedAtBetween(UUID tenantId, LocalDateTime from, LocalDateTime to);

    // Busca simples por tracking / transportadora
    List<PackId> findAllByTenantIdAndTrackingCodeContainingIgnoreCase(UUID tenantId, String trackingCode);
    List<PackId> findAllByTenantIdAndCarrierContainingIgnoreCase(UUID tenantId, String carrier);

    Optional<PackId> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    List<PackId> findAllByTenantIdAndDeletedFalse(UUID tenantId);

    Optional<PackId> findByTenantIdAndPackageCodeHashAndDeletedFalse(UUID tenantId, String packageCodeHash);

}
