package com.packid.api.domain.repository;

import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.RegistryEntry.EntryType;
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
    List<RegistryEntry> findAllByTenantIdAndDeletedFalseOrderByNameAsc(UUID tenantId);
    List<RegistryEntry> findAllByTenantIdAndEntryTypeAndDeletedFalseOrderByNameAsc(UUID tenantId, EntryType entryType);

    List<RegistryEntry> findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByNameAsc(
            UUID tenantId, String block, String apartment);

    List<RegistryEntry> findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByNameAsc(
            UUID tenantId, UUID occupancyId);

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
