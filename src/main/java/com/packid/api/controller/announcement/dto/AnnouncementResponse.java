package com.packid.api.controller.announcement.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        String title,
        String bodyHtml,
        String bodyText,
        LocalDateTime publishedAt,
        String createdBy,
        Integer recipientCount,
        Integer deliveredCount,
        Integer failedCount,
        String deliveryStatus
) {}
