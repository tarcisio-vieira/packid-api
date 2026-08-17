package com.packid.api.controller.resident.dto;

import java.util.UUID;

public record ResidentSessionResponse(
        UUID occupancyId,
        String tenantName,
        String tenantSlug,
        String block,
        String apartment,
        String username,
        boolean mustChangePassword
) {}
