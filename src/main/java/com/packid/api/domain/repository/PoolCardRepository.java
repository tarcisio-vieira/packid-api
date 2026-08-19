package com.packid.api.domain.repository;

import com.packid.api.domain.model.PoolCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PoolCardRepository extends JpaRepository<PoolCard, UUID> {
    Optional<PoolCard> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    List<PoolCard> findAllByTenantIdAndDeletedFalseOrderByValidUntilDesc(UUID tenantId);
    List<PoolCard> findAllByTenantIdAndResidentRegistryEntryIdAndDeletedFalseOrderByIssueDateDesc(UUID tenantId, UUID residentRegistryEntryId);
    Optional<PoolCard> findFirstByTenantIdAndResidentRegistryEntryIdAndDeletedFalseOrderByIssueDateDesc(UUID tenantId, UUID residentRegistryEntryId);


    @Query("""
            select pc
              from PoolCard pc
              join fetch pc.resident r
             where pc.tenantId = :tenantId
               and pc.deleted = false
               and r.deleted = false
               and (
                    :search = ''
                    or lower(coalesce(r.name, '')) like concat('%', :search, '%')
                    or lower(coalesce(r.block, '')) like concat('%', :search, '%')
                    or lower(coalesce(r.apartment, '')) like concat('%', :search, '%')
                    or lower(concat(coalesce(r.block, ''), coalesce(r.apartment, ''))) like concat('%', :search, '%')
               )
             order by pc.validUntil desc, pc.issueDate desc, pc.id asc
            """)
    List<PoolCard> searchAll(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search
    );

    @Query(value = """
            select pc
              from PoolCard pc
              join fetch pc.resident r
             where pc.tenantId = :tenantId
               and pc.deleted = false
               and r.deleted = false
               and (
                    :search = ''
                    or lower(coalesce(r.name, '')) like concat('%', :search, '%')
                    or lower(coalesce(r.block, '')) like concat('%', :search, '%')
                    or lower(coalesce(r.apartment, '')) like concat('%', :search, '%')
                    or lower(concat(coalesce(r.block, ''), coalesce(r.apartment, ''))) like concat('%', :search, '%')
               )
             order by pc.validUntil desc, pc.issueDate desc, pc.id asc
            """,
            countQuery = """
            select count(pc)
              from PoolCard pc
              join pc.resident r
             where pc.tenantId = :tenantId
               and pc.deleted = false
               and r.deleted = false
               and (
                    :search = ''
                    or lower(coalesce(r.name, '')) like concat('%', :search, '%')
                    or lower(coalesce(r.block, '')) like concat('%', :search, '%')
                    or lower(coalesce(r.apartment, '')) like concat('%', :search, '%')
                    or lower(concat(coalesce(r.block, ''), coalesce(r.apartment, ''))) like concat('%', :search, '%')
               )
            """)
    Page<PoolCard> searchPage(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            Pageable pageable
    );
}
