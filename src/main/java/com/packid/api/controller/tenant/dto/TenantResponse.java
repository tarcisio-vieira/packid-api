package com.packid.api.controller.tenant.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String slug,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
