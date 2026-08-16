package com.packid.api.service.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UnitChangeEmailEvent(
        UUID tenantId,
        String block,
        String apartment,
        List<String> recipients,
        String changeType,
        String title,
        String details,
        String actor,
        LocalDateTime occurredAt
) {
}
