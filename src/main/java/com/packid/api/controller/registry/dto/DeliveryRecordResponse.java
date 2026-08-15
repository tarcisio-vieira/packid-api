package com.packid.api.controller.registry.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryRecordResponse(
        UUID id,
        UUID deliveryPersonRegistryEntryId,
        String deliveryPersonName,
        String company,
        String document,
        String phone,
        String block,
        String apartment,
        LocalDateTime deliveredAt,
        Boolean authorizedToEnter,
        String notes
) {}
