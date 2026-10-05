package com.packid.api.controller.amenity.dto;

import com.packid.api.domain.model.AmenityReservation;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record AmenityReservationResponse(
        UUID id, UUID amenitySpaceId, String amenitySpaceName,
        UUID occupancyId, String block, String apartment,
        LocalDate reservationDate, AmenityReservation.Status status,
        BigDecimal usageFee, LocalDateTime requestedAt,
        LocalDateTime cancelledAt, String cancelledBy, String notes,
        List<AmenityGuestResponse> guests
) {}
