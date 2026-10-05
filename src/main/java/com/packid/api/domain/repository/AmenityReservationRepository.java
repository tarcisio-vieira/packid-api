package com.packid.api.domain.repository;

import com.packid.api.domain.model.AmenityReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.*;

public interface AmenityReservationRepository extends JpaRepository<AmenityReservation, UUID> {
    Optional<AmenityReservation> findByTenantIdAndIdAndDeletedFalse(UUID tenantId, UUID id);
    boolean existsByTenantIdAndAmenitySpaceIdAndReservationDateAndStatusAndDeletedFalse(UUID tenantId, UUID amenitySpaceId, LocalDate reservationDate, AmenityReservation.Status status);
    List<AmenityReservation> findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByReservationDateDesc(UUID tenantId, UUID occupancyId);
    List<AmenityReservation> findAllByTenantIdAndAmenitySpaceIdAndStatusAndDeletedFalseAndReservationDateBetweenOrderByReservationDateAsc(
            UUID tenantId,
            UUID amenitySpaceId,
            AmenityReservation.Status status,
            LocalDate from,
            LocalDate to);

    List<AmenityReservation> findAllByTenantIdAndDeletedFalseOrderByReservationDateAsc(UUID tenantId);

}
