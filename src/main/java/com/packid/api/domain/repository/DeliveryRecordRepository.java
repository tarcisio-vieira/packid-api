package com.packid.api.domain.repository;

import com.packid.api.domain.model.DeliveryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, UUID> {
    List<DeliveryRecord> findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByDeliveredAtDesc(
            UUID tenantId, String block, String apartment);

    List<DeliveryRecord> findAllByTenantIdAndDeliveryPersonRegistryEntryIdAndDeletedFalseOrderByDeliveredAtDesc(
            UUID tenantId, UUID deliveryPersonRegistryEntryId);
}
