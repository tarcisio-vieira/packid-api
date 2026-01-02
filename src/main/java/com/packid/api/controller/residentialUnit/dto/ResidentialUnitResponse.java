package com.packid.api.controller.residentialUnit.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ResidentialUnitResponse(
        UUID id,
        UUID tenantId,
        UUID condominiumId,
        String code,
        String name,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
