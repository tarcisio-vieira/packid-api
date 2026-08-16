package com.packid.api.domain.repository;

import com.packid.api.domain.model.ServiceRecord;
import com.packid.api.domain.model.ServiceRecord.ServiceScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, UUID> {
    List<ServiceRecord> findAllByTenantIdAndServiceProviderRegistryEntryIdAndDeletedFalseOrderByPerformedAtDesc(UUID tenantId, UUID providerId);
    List<ServiceRecord> findAllByTenantIdAndServiceScopeAndDeletedFalseOrderByPerformedAtDesc(UUID tenantId, ServiceScope scope);

    @Query("""
            select s from ServiceRecord s
             where s.tenantId = :tenantId and s.serviceScope = :scope
               and lower(trim(s.block)) = lower(trim(:block)) and lower(trim(s.apartment)) = lower(trim(:apartment))
               and s.deleted = false order by s.performedAt desc
            """)
    List<ServiceRecord> findByUnit(@Param("tenantId") UUID tenantId, @Param("scope") ServiceScope scope,
                                      @Param("block") String block, @Param("apartment") String apartment);

    @Query("""
            select s from ServiceRecord s
             where s.tenantId = :tenantId and s.serviceScope = :scope
               and lower(trim(s.block)) = lower(trim(:block)) and lower(trim(s.apartment)) = lower(trim(:apartment))
               and s.deleted = false and s.performedAt >= :fromTs order by s.performedAt desc
            """)
    List<ServiceRecord> findByUnitFrom(@Param("tenantId") UUID tenantId, @Param("scope") ServiceScope scope,
                                          @Param("block") String block, @Param("apartment") String apartment,
                                          @Param("fromTs") LocalDateTime fromTs);

    @Query("""
            select s from ServiceRecord s
             where s.tenantId = :tenantId and s.serviceScope = :scope
               and lower(trim(s.block)) = lower(trim(:block)) and lower(trim(s.apartment)) = lower(trim(:apartment))
               and s.deleted = false and s.performedAt < :toTs order by s.performedAt desc
            """)
    List<ServiceRecord> findByUnitUntil(@Param("tenantId") UUID tenantId, @Param("scope") ServiceScope scope,
                                           @Param("block") String block, @Param("apartment") String apartment,
                                           @Param("toTs") LocalDateTime toTs);

    @Query("""
            select s from ServiceRecord s
             where s.tenantId = :tenantId and s.serviceScope = :scope
               and lower(trim(s.block)) = lower(trim(:block)) and lower(trim(s.apartment)) = lower(trim(:apartment))
               and s.deleted = false and s.performedAt >= :fromTs and s.performedAt < :toTs order by s.performedAt desc
            """)
    List<ServiceRecord> findByUnitBetween(@Param("tenantId") UUID tenantId, @Param("scope") ServiceScope scope,
                                             @Param("block") String block, @Param("apartment") String apartment,
                                             @Param("fromTs") LocalDateTime fromTs, @Param("toTs") LocalDateTime toTs);
}
