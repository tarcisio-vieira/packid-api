package com.packid.api.domain.repository;

import com.packid.api.domain.model.PackId;
import com.packid.api.domain.type.PackageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    interface PackIdRecentRow {
        UUID getId();
        String getApartment();
        String getPackageCode();        // código “interno” (se quiser manter)
        String getLabelPackageCode();   // NOVO: digitado no front
        Instant getArrivedAt();
        String getCreatedBy();
    }

    @Query(value = """
    SELECT
      p.id AS id,
      ru.code AS apartment,
      p.package_code AS packageCode,
      p.label_package_code AS labelPackageCode,
      p.arrived_at AS arrivedAt,
      p.created_by AS createdBy
    FROM public.pack_id p
    JOIN public.residential_unit ru
      ON ru.tenant_id = p.tenant_id
     AND ru.id = p.residential_unit_id
    WHERE p.tenant_id = :tenantId
      AND p.deleted = false
      AND p.arrived_at >= COALESCE(CAST(:fromTs AS timestamp), '-infinity'::timestamp)
      AND p.arrived_at <  COALESCE(CAST(:toTs   AS timestamp), 'infinity'::timestamp)
    ORDER BY p.arrived_at DESC
    LIMIT :limit
    """, nativeQuery = true)
    List<PackIdRecentRow> findRecentByTenant(
            @Param("tenantId") UUID tenantId,
            @Param("limit") int limit,
            @Param("fromTs") java.sql.Timestamp fromTs,
            @Param("toTs") java.sql.Timestamp toTs
    );
}
