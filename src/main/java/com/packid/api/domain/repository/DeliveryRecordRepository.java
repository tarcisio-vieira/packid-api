package com.packid.api.domain.repository;

import com.packid.api.domain.model.DeliveryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, UUID> {
    List<DeliveryRecord> findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByDeliveredAtDesc(
            UUID tenantId, String block, String apartment);

    List<DeliveryRecord> findAllByTenantIdAndDeliveryPersonRegistryEntryIdAndDeletedFalseOrderByDeliveredAtDesc(
            UUID tenantId, UUID deliveryPersonRegistryEntryId);

    @Query("""
            select d
              from DeliveryRecord d
             where d.tenantId = :tenantId
               and lower(trim(d.block)) = lower(trim(:block))
               and lower(trim(d.apartment)) = lower(trim(:apartment))
               and d.deleted = false
               and d.deliveredAt >= :fromTs
             order by d.deliveredAt desc
            """)
    List<DeliveryRecord> findByUnitFrom(
            @Param("tenantId") UUID tenantId,
            @Param("block") String block,
            @Param("apartment") String apartment,
            @Param("fromTs") LocalDateTime fromTs
    );

    @Query("""
            select d
              from DeliveryRecord d
             where d.tenantId = :tenantId
               and lower(trim(d.block)) = lower(trim(:block))
               and lower(trim(d.apartment)) = lower(trim(:apartment))
               and d.deleted = false
               and d.deliveredAt < :toTs
             order by d.deliveredAt desc
            """)
    List<DeliveryRecord> findByUnitUntil(
            @Param("tenantId") UUID tenantId,
            @Param("block") String block,
            @Param("apartment") String apartment,
            @Param("toTs") LocalDateTime toTs
    );

    @Query("""
            select d
              from DeliveryRecord d
             where d.tenantId = :tenantId
               and lower(trim(d.block)) = lower(trim(:block))
               and lower(trim(d.apartment)) = lower(trim(:apartment))
               and d.deleted = false
               and d.deliveredAt >= :fromTs
               and d.deliveredAt < :toTs
             order by d.deliveredAt desc
            """)
    List<DeliveryRecord> findByUnitBetween(
            @Param("tenantId") UUID tenantId,
            @Param("block") String block,
            @Param("apartment") String apartment,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );
}
