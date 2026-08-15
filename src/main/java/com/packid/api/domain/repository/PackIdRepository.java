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

        String getBookPage();

        String getBlock();

        String getApartment();

        String getResidentFullName();

        String getPackageCode();

        String getLabelPackageCode();

        String getObservations();

        Instant getArrivedAt();

        String getCreatedBy();
    }

    @Query(value = """
            SELECT
              p.id AS id,
              COALESCE(
                p.book_page,
                CASE WHEN p.building_block ~ '^[0-9]{3}$' THEN p.building_block ELSE NULL END
              ) AS bookPage,
              CASE
                WHEN p.apartment IS NOT NULL THEN p.building_block
                WHEN p.building_block ~ '^[0-9]{3}$' AND ru.code ~ '^[1-4][0-9]{3,4}$' THEN SUBSTRING(ru.code FROM 1 FOR 1)
                ELSE p.building_block
              END AS block,
              CASE
                WHEN p.apartment IS NOT NULL THEN p.apartment
                WHEN p.building_block ~ '^[0-9]{3}$' AND ru.code ~ '^[1-4][0-9]{3,4}$' THEN SUBSTRING(ru.code FROM 2)
                ELSE ru.code
              END AS apartment,
              pe.full_name AS residentFullName,
              p.package_code AS packageCode,
              p.label_package_code AS labelPackageCode,
              p.observations AS observations,
              p.arrived_at AS arrivedAt,
              p.created_by AS createdBy
            FROM public.pack_id p
            JOIN public.residential_unit ru
              ON ru.tenant_id = p.tenant_id
             AND ru.id = p.residential_unit_id
            LEFT JOIN public.person pe
              ON pe.tenant_id = p.tenant_id
             AND pe.id = p.person_id
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
    @Query(value = """
            SELECT
              p.id AS id,
              COALESCE(
                p.book_page,
                CASE WHEN p.building_block ~ '^[0-9]{3}$' THEN p.building_block ELSE NULL END
              ) AS bookPage,
              CASE
                WHEN p.apartment IS NOT NULL THEN p.building_block
                WHEN p.building_block ~ '^[0-9]{3}$' AND ru.code ~ '^[1-4][0-9]{3,4}$' THEN SUBSTRING(ru.code FROM 1 FOR 1)
                ELSE p.building_block
              END AS block,
              CASE
                WHEN p.apartment IS NOT NULL THEN p.apartment
                WHEN p.building_block ~ '^[0-9]{3}$' AND ru.code ~ '^[1-4][0-9]{3,4}$' THEN SUBSTRING(ru.code FROM 2)
                ELSE ru.code
              END AS apartment,
              pe.full_name AS residentFullName,
              p.package_code AS packageCode,
              p.label_package_code AS labelPackageCode,
              p.observations AS observations,
              p.arrived_at AS arrivedAt,
              p.created_by AS createdBy
            FROM public.pack_id p
            JOIN public.residential_unit ru
              ON ru.tenant_id = p.tenant_id
             AND ru.id = p.residential_unit_id
            LEFT JOIN public.person pe
              ON pe.tenant_id = p.tenant_id
             AND pe.id = p.person_id
            WHERE p.tenant_id = :tenantId
              AND p.deleted = false
              AND p.arrived_at >= COALESCE(CAST(:fromTs AS timestamp), '-infinity'::timestamp)
              AND p.arrived_at <  COALESCE(CAST(:toTs   AS timestamp), 'infinity'::timestamp)
              AND (
                    (
                        LOWER(TRIM(COALESCE(p.building_block, ''))) = LOWER(TRIM(:block))
                        AND LOWER(TRIM(COALESCE(p.apartment, ''))) = LOWER(TRIM(:apartment))
                    )
                    OR (
                        p.apartment IS NULL
                        AND p.building_block ~ '^[0-9]{3}$'
                        AND ru.code ~ '^[1-4][0-9]{3,4}$'
                        AND SUBSTRING(ru.code FROM 1 FOR 1) = TRIM(:block)
                        AND SUBSTRING(ru.code FROM 2) = TRIM(:apartment)
                    )
                    OR (
                        p.apartment IS NULL
                        AND p.building_block IS NULL
                        AND EXISTS (
                            SELECT 1
                            FROM public.registry_entry re
                            WHERE re.tenant_id = p.tenant_id
                              AND re.person_id = p.person_id
                              AND re.entry_type = 'RESIDENT'
                              AND re.deleted = false
                              AND LOWER(TRIM(COALESCE(re.block, ''))) = LOWER(TRIM(:block))
                              AND LOWER(TRIM(COALESCE(re.apartment, ''))) = LOWER(TRIM(:apartment))
                        )
                    )
                  )
            ORDER BY p.arrived_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<PackIdRecentRow> findByUnit(
            @Param("tenantId") UUID tenantId,
            @Param("block") String block,
            @Param("apartment") String apartment,
            @Param("fromTs") java.sql.Timestamp fromTs,
            @Param("toTs") java.sql.Timestamp toTs,
            @Param("limit") int limit
    );

}
