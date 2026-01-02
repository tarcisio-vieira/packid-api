package com.packid.api.controller.condominium.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CondominiumCreateRequest(
        @NotNull UUID tenantId,
        @NotBlank String name,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zipCode
) {}
