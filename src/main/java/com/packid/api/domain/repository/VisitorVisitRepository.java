package com.packid.api.domain.repository;

import com.packid.api.domain.model.VisitorVisit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisitorVisitRepository extends JpaRepository<VisitorVisit, UUID> {
    List<VisitorVisit> findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByVisitedAtDesc(
            UUID tenantId, String block, String apartment);

    List<VisitorVisit> findAllByTenantIdAndVisitorRegistryEntryIdAndDeletedFalseOrderByVisitedAtDesc(
            UUID tenantId, UUID visitorRegistryEntryId);
}
