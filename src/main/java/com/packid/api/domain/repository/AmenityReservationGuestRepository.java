package com.packid.api.domain.repository;

import com.packid.api.domain.model.AmenityReservationGuest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AmenityReservationGuestRepository extends JpaRepository<AmenityReservationGuest, UUID> {
    List<AmenityReservationGuest> findAllByTenantIdAndReservationIdAndDeletedFalseOrderByNameAsc(UUID tenantId, UUID reservationId);
    void deleteAllByReservationId(UUID reservationId);
}
