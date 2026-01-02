package com.packid.api.controller.unitOfMeasure.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UnitOfMeasureResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        String symbol,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
