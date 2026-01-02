package com.packid.api.controller.condominium.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record CondominiumResponse(
        UUID id,
        UUID tenantId,
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zipCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
