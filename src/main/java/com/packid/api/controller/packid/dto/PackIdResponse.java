package com.packid.api.controller.packid.dto;

import com.packid.api.domain.type.PackageType;

import java.time.LocalDateTime;
import java.util.UUID;

public record PackIdResponse(
        UUID id,
        UUID tenantId,
        UUID residentialUnitId,
        UUID personId,
        UUID registeredByUserId,

        PackageType packageType,

        String packageCode,
        String packageCodeHash,

        String carrier,
        String trackingCode,
        String description,

        LocalDateTime arrivedAt,

        String whatsappMessageId,
        LocalDateTime whatsappSentAt,
        LocalDateTime whatsappDeliveredAt,
        LocalDateTime whatsappReadAt,

        LocalDateTime residentAcknowledgedAt,

        LocalDateTime handedOverAt,
        UUID handedOverByUserId,

        String observations,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
