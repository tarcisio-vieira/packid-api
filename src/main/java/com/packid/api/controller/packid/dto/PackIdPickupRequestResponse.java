package com.packid.api.controller.packid.dto;

import java.time.Instant;
import java.util.UUID;

public record PackIdPickupRequestResponse(
        UUID id,
        String block,
        String apartment,
        String residentFullName,
        String packageCode,
        Instant arrivedAt,
        Instant requestedAt
) {}
