package com.packid.api.controller.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        BigDecimal unitPrice,
        UUID unitOfMeasureId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
