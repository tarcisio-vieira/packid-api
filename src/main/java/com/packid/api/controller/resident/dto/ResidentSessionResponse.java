package com.packid.api.controller.resident.dto;

import java.util.UUID;

public record ResidentSessionResponse(
        UUID residentId,
        String residentName,
        String tenantName,
        String tenantSlug,
        String block,
        String apartment
) {}
