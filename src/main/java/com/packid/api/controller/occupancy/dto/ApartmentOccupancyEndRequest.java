package com.packid.api.controller.occupancy.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record ApartmentOccupancyEndRequest(
        @NotBlank String block,
        @NotBlank String apartment,
        LocalDate endDate
) {}
