package com.packid.api.domain.repository;

import com.packid.api.domain.model.SpaceAccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceAccessRequestRepository extends JpaRepository<SpaceAccessRequest, UUID> {

    Optional<SpaceAccessRequest> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);

    List<SpaceAccessRequest> findAllByTenantIdAndStatusInAndDeletedFalseOrderByRequestedAtAsc(
            UUID tenantId, List<SpaceAccessRequest.Status> statuses);

    List<SpaceAccessRequest> findAllByTenantIdAndResidentRegistryEntryIdAndDeletedFalseOrderByRequestedAtDesc(
            UUID tenantId, UUID residentRegistryEntryId);

    Optional<SpaceAccessRequest> findFirstByTenantIdAndResidentRegistryEntryIdAndSpaceTypeAndStatusInAndDeletedFalseOrderByRequestedAtDesc(
            UUID tenantId, UUID residentRegistryEntryId, SpaceAccessRequest.SpaceType spaceType,
            List<SpaceAccessRequest.Status> statuses);

    @Query("""
            select s from SpaceAccessRequest s
             where s.tenantId = :tenantId
               and s.deleted = false
               and lower(s.block) = lower(:block)
               and lower(s.apartment) = lower(:apartment)
             order by s.requestedAt desc
            """)
    List<SpaceAccessRequest> findByUnit(
            @Param("tenantId") UUID tenantId,
            @Param("block") String block,
            @Param("apartment") String apartment
    );

    @Query("""
            select s from SpaceAccessRequest s
             where s.tenantId = :tenantId
               and s.deleted = false
               and lower(s.block) = lower(:block)
               and lower(s.apartment) = lower(:apartment)
               and s.occupancyId = :occupancyId
             order by s.requestedAt desc
            """)
    List<SpaceAccessRequest> findByUnitAndOccupancy(
            @Param("tenantId") UUID tenantId,
            @Param("block") String block,
            @Param("apartment") String apartment,
            @Param("occupancyId") UUID occupancyId
    );

    @Query("""
            select s from SpaceAccessRequest s
             where s.tenantId = :tenantId
               and s.deleted = false
               and s.requestedAt >= :from
               and s.requestedAt < :to
             order by s.requestedAt desc
            """)
    List<SpaceAccessRequest> reportAll(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            select s from SpaceAccessRequest s
             where s.tenantId = :tenantId
               and s.deleted = false
               and s.spaceType = :spaceType
               and s.requestedAt >= :from
               and s.requestedAt < :to
             order by s.requestedAt desc
            """)
    List<SpaceAccessRequest> reportBySpaceType(
            @Param("tenantId") UUID tenantId,
            @Param("spaceType") SpaceAccessRequest.SpaceType spaceType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
