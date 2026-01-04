package com.packid.api.controller.packid.dto;

import java.time.Instant;
import java.util.UUID;

public record PackIdRecentResponse(
        UUID id,
        String apartment,
        String packageCode,
        String labelPackageCode,
        Instant arrivedAt,
        String createdBy
) {}
