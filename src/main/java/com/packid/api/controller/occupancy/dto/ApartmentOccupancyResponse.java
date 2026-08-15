package com.packid.api.controller.occupancy.dto;

import com.packid.api.domain.model.ApartmentOccupancy;

import java.time.LocalDate;
import java.util.UUID;

public record ApartmentOccupancyResponse(
        UUID id,
        String block,
        String apartment,
        LocalDate startDate,
        LocalDate endDate,
        ApartmentOccupancy.Status status,
        String notes
) {}
