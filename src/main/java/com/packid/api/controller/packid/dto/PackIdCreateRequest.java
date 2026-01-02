package com.packid.api.controller.packid.dto;

import com.packid.api.domain.type.PackageType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record PackIdCreateRequest(
        @NotNull UUID tenantId,
        @NotNull UUID residentialUnitId,
        @NotNull UUID personId,

        UUID registeredByUserId,

        @NotNull PackageType packageType,

        String packageCode,
        String carrier,
        String trackingCode,
        String description,

        LocalDateTime arrivedAt,

        String observations
) {}
