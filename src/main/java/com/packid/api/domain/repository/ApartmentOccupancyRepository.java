package com.packid.api.domain.repository;

import com.packid.api.domain.model.ApartmentOccupancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApartmentOccupancyRepository extends JpaRepository<ApartmentOccupancy, UUID> {
    Optional<ApartmentOccupancy> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);

    Optional<ApartmentOccupancy> findFirstByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndStatusAndDeletedFalse(
            UUID tenantId,
            String block,
            String apartment,
            ApartmentOccupancy.Status status
    );


    Optional<ApartmentOccupancy> findFirstByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndStatusAndStartDateLessThanEqualAndDeletedFalseOrderByStartDateAsc(
            UUID tenantId,
            String block,
            String apartment,
            ApartmentOccupancy.Status status,
            java.time.LocalDate startDate
    );

    List<ApartmentOccupancy> findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByStartDateDescCreatedAtDesc(
            UUID tenantId,
            String block,
            String apartment
    );
}
