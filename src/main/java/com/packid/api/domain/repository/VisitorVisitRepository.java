package com.packid.api.domain.repository;

import com.packid.api.domain.model.VisitorVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface VisitorVisitRepository extends JpaRepository<VisitorVisit, UUID> {
    List<VisitorVisit> findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByVisitedAtDesc(
            UUID tenantId, String block, String apartment);

    List<VisitorVisit> findAllByTenantIdAndVisitorRegistryEntryIdAndDeletedFalseOrderByVisitedAtDesc(
            UUID tenantId, UUID visitorRegistryEntryId);

    @Query("""
            select v
              from VisitorVisit v
             where v.tenantId = :tenantId
               and lower(trim(v.block)) = lower(trim(:block))
               and lower(trim(v.apartment)) = lower(trim(:apartment))
               and v.deleted = false
               and v.visitedAt >= :fromTs
             order by v.visitedAt desc
            """)
    List<VisitorVisit> findByUnitFrom(
            @Param("tenantId") UUID tenantId,
            @Param("block") String block,
            @Param("apartment") String apartment,
            @Param("fromTs") LocalDateTime fromTs
    );

    @Query("""
            select v
              from VisitorVisit v
             where v.tenantId = :tenantId
               and lower(trim(v.block)) = lower(trim(:block))
               and lower(trim(v.apartment)) = lower(trim(:apartment))
               and v.deleted = false
               and v.visitedAt < :toTs
             order by v.visitedAt desc
            """)
    List<VisitorVisit> findByUnitUntil(
            @Param("tenantId") UUID tenantId,
            @Param("block") String block,
            @Param("apartment") String apartment,
            @Param("toTs") LocalDateTime toTs
    );

    @Query("""
            select v
              from VisitorVisit v
             where v.tenantId = :tenantId
               and lower(trim(v.block)) = lower(trim(:block))
               and lower(trim(v.apartment)) = lower(trim(:apartment))
               and v.deleted = false
               and v.visitedAt >= :fromTs
               and v.visitedAt < :toTs
             order by v.visitedAt desc
            """)
    List<VisitorVisit> findByUnitBetween(
            @Param("tenantId") UUID tenantId,
            @Param("block") String block,
            @Param("apartment") String apartment,
            @Param("fromTs") LocalDateTime fromTs,
            @Param("toTs") LocalDateTime toTs
    );
}
