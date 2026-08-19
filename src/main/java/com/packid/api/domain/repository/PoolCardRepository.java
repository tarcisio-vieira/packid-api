package com.packid.api.domain.repository;

import com.packid.api.domain.model.PoolCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PoolCardRepository extends JpaRepository<PoolCard, UUID> {
    Optional<PoolCard> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    List<PoolCard> findAllByTenantIdAndDeletedFalseOrderByValidUntilDesc(UUID tenantId);
    List<PoolCard> findAllByTenantIdAndResidentRegistryEntryIdAndDeletedFalseOrderByIssueDateDesc(UUID tenantId, UUID residentRegistryEntryId);
    Optional<PoolCard> findFirstByTenantIdAndResidentRegistryEntryIdAndDeletedFalseOrderByIssueDateDesc(UUID tenantId, UUID residentRegistryEntryId);
}
