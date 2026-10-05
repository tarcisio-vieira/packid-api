package com.packid.api.controller.amenity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AmenityReservationRequest(
        @NotNull UUID amenitySpaceId,
        @NotNull LocalDate reservationDate,
        String notes,
        List<@Valid AmenityGuestRequest> guests
) {}
