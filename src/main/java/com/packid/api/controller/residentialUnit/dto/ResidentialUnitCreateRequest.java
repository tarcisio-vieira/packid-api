package com.packid.api.controller.residentialUnit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ResidentialUnitCreateRequest(
        @NotNull UUID tenantId,
        @NotNull UUID condominiumId,
        String code,
        String name,
        @NotBlank String block,
        @NotBlank String apartment,
        Boolean active
) {}
