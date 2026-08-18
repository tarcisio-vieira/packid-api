package com.packid.api.domain.repository;

import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.RegistryEntry.EntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistryEntryRepository extends JpaRepository<RegistryEntry, UUID> {
    Optional<RegistryEntry> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    Optional<RegistryEntry> findByTenantIdAndEntryTypeAndDocumentIgnoreCaseAndDeletedFalse(
            UUID tenantId, EntryType entryType, String document);
    Optional<RegistryEntry> findByTenantIdAndEntryTypeAndResidentUsernameIgnoreCaseAndActiveTrueAndDeletedFalse(
            UUID tenantId, EntryType entryType, String residentUsername);

    Optional<RegistryEntry> findByTenantIdAndResidentUsernameIgnoreCaseAndDeletedFalse(
            UUID tenantId, String residentUsername);
    List<RegistryEntry> findAllByTenantIdAndDeletedFalseOrderByNameAsc(UUID tenantId);
    List<RegistryEntry> findAllByTenantIdAndEntryTypeAndDeletedFalseOrderByNameAsc(UUID tenantId, EntryType entryType);


    @Query(value = """
            SELECT re.*
              FROM registry_entry re
             WHERE re.tenant_id = :tenantId
               AND re.entry_type = :entryType
               AND re.deleted = false
               AND (:includeInactive = true OR re.active = true)
               AND (:ownersOnly = false OR re.unit_owner = true)
               AND (
                    :search = ''
                    OR translate(lower(concat_ws(' ',
                        re.name,
                        re.document,
                        re.phone,
                        re.email,
                        re.block,
                        re.apartment,
                        coalesce(re.block, '') || coalesce(re.apartment, ''),
                        re.company,
                        re.owner_name,
                        re.brand,
                        re.model,
                        re.color,
                        re.identifier,
                        re.species,
                        re.breed,
                        re.parking_space,
                        re.notes
                    )), 'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn')
                    LIKE concat('%', :search, '%')
               )
             ORDER BY
               CASE WHEN :sortField = 'name' AND :sortDirection = 'asc'
                    THEN translate(lower(coalesce(re.name, '')), 'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn') END ASC,
               CASE WHEN :sortField = 'name' AND :sortDirection = 'desc'
                    THEN translate(lower(coalesce(re.name, '')), 'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn') END DESC,

               CASE WHEN :sortField = 'owner' AND :sortDirection = 'asc' THEN re.unit_owner END ASC,
               CASE WHEN :sortField = 'owner' AND :sortDirection = 'desc' THEN re.unit_owner END DESC,
               CASE WHEN :sortField = 'owner'
                    THEN CASE WHEN coalesce(re.block, '') ~ '^[0-9]+$' THEN lpad(re.block, 20, '0') ELSE lower(coalesce(re.block, '')) END END ASC,
               CASE WHEN :sortField = 'owner'
                    THEN CASE WHEN coalesce(re.apartment, '') ~ '^[0-9]+$' THEN lpad(re.apartment, 20, '0') ELSE lower(coalesce(re.apartment, '')) END END ASC,
               CASE WHEN :sortField = 'owner'
                    THEN translate(lower(coalesce(re.name, '')), 'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn') END ASC,

               CASE WHEN :sortField = 'unit' AND :sortDirection = 'asc'
                    THEN CASE WHEN coalesce(re.block, '') ~ '^[0-9]+$' THEN lpad(re.block, 20, '0') ELSE lower(coalesce(re.block, '')) END END ASC,
               CASE WHEN :sortField = 'unit' AND :sortDirection = 'asc'
                    THEN CASE WHEN coalesce(re.apartment, '') ~ '^[0-9]+$' THEN lpad(re.apartment, 20, '0') ELSE lower(coalesce(re.apartment, '')) END END ASC,
               CASE WHEN :sortField = 'unit' AND :sortDirection = 'desc'
                    THEN CASE WHEN coalesce(re.block, '') ~ '^[0-9]+$' THEN lpad(re.block, 20, '0') ELSE lower(coalesce(re.block, '')) END END DESC,
               CASE WHEN :sortField = 'unit' AND :sortDirection = 'desc'
                    THEN CASE WHEN coalesce(re.apartment, '') ~ '^[0-9]+$' THEN lpad(re.apartment, 20, '0') ELSE lower(coalesce(re.apartment, '')) END END DESC,
               CASE WHEN :sortField = 'unit' AND re.entry_type = 'RESIDENT' THEN re.unit_owner END DESC,
               CASE WHEN :sortField = 'unit'
                    THEN translate(lower(coalesce(re.name, '')), 'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn') END ASC,
               re.id ASC
            """,
            countQuery = """
            SELECT count(*)
              FROM registry_entry re
             WHERE re.tenant_id = :tenantId
               AND re.entry_type = :entryType
               AND re.deleted = false
               AND (:includeInactive = true OR re.active = true)
               AND (:ownersOnly = false OR re.unit_owner = true)
               AND (:sortField = :sortField)
               AND (:sortDirection = :sortDirection)
               AND (
                    :search = ''
                    OR translate(lower(concat_ws(' ',
                        re.name,
                        re.document,
                        re.phone,
                        re.email,
                        re.block,
                        re.apartment,
                        coalesce(re.block, '') || coalesce(re.apartment, ''),
                        re.company,
                        re.owner_name,
                        re.brand,
                        re.model,
                        re.color,
                        re.identifier,
                        re.species,
                        re.breed,
                        re.parking_space,
                        re.notes
                    )), 'áàâãäéèêëíìîïóòôõöúùûüçñ', 'aaaaaeeeeiiiiooooouuuucn')
                    LIKE concat('%', :search, '%')
               )
            """,
            nativeQuery = true)
    Page<RegistryEntry> searchPage(
            @Param("tenantId") UUID tenantId,
            @Param("entryType") String entryType,
            @Param("includeInactive") boolean includeInactive,
            @Param("ownersOnly") boolean ownersOnly,
            @Param("search") String search,
            @Param("sortField") String sortField,
            @Param("sortDirection") String sortDirection,
            Pageable pageable
    );

    List<RegistryEntry> findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByNameAsc(
            UUID tenantId, String block, String apartment);

    List<RegistryEntry> findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByNameAsc(
            UUID tenantId, UUID occupancyId);


    List<RegistryEntry> findAllByTenantIdAndOccupancyIdAndEntryTypeAndActiveTrueAndDeletedFalseOrderByNameAsc(
            UUID tenantId, UUID occupancyId, EntryType entryType);

    List<RegistryEntry> findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndActiveTrueAndDeletedFalseOrderByNameAsc(
            UUID tenantId, String block, String apartment);


    List<RegistryEntry> findAllByTenantIdAndEntryTypeAndBlockIgnoreCaseAndApartmentIgnoreCaseAndActiveTrueAndDeletedFalseOrderByNameAsc(
            UUID tenantId, EntryType entryType, String block, String apartment);

    List<RegistryEntry> findAllByTenantIdAndEntryTypeAndApartmentIgnoreCaseAndActiveTrueAndDeletedFalseOrderByNameAsc(
            UUID tenantId, EntryType entryType, String apartment);

    @Query("""
            select re.email
              from RegistryEntry re
             where re.tenantId = :tenantId
               and re.entryType = :entryType
               and lower(trim(re.block)) = lower(trim(:block))
               and lower(trim(re.apartment)) = lower(trim(:apartment))
               and re.active = true
               and re.deleted = false
               and re.email is not null
            order by re.name asc
            """)
    List<String> findActiveResidentEmailsByUnit(
            @Param("tenantId") UUID tenantId,
            @Param("entryType") EntryType entryType,
            @Param("block") String block,
            @Param("apartment") String apartment
    );
}
