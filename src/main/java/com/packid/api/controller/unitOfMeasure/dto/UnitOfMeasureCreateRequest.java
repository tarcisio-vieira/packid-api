package com.packid.api.controller.unitOfMeasure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UnitOfMeasureCreateRequest(
        @NotNull UUID tenantId,
        @NotBlank String code,
        @NotBlank String name,
        String description,
        String symbol
) {}
