package com.packid.api.domain.repository;

import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.RegistryEntry.EntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegistryEntryRepository extends JpaRepository<RegistryEntry, UUID> {
    Optional<RegistryEntry> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    List<RegistryEntry> findAllByTenantIdAndDeletedFalseOrderByNameAsc(UUID tenantId);
    List<RegistryEntry> findAllByTenantIdAndEntryTypeAndDeletedFalseOrderByNameAsc(UUID tenantId, EntryType entryType);

    List<RegistryEntry> findAllByTenantIdAndEntryTypeAndBlockIgnoreCaseAndApartmentIgnoreCaseAndActiveTrueAndDeletedFalseOrderByNameAsc(
            UUID tenantId, EntryType entryType, String block, String apartment);

    List<RegistryEntry> findAllByTenantIdAndEntryTypeAndApartmentIgnoreCaseAndActiveTrueAndDeletedFalseOrderByNameAsc(
            UUID tenantId, EntryType entryType, String apartment);
}
