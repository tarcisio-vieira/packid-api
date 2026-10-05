package com.packid.api.domain.repository;

import com.packid.api.domain.model.BillingCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingChargeRepository extends JpaRepository<BillingCharge, UUID> {
    List<BillingCharge> findAllByTenantIdAndDeletedFalseOrderByDueDateDesc(UUID tenantId);
    List<BillingCharge> findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByDueDateDesc(UUID tenantId, UUID occupancyId);
    Optional<BillingCharge> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
}
