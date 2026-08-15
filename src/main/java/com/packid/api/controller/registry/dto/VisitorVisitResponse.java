package com.packid.api.controller.registry.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record VisitorVisitResponse(
        UUID id,
        UUID visitorRegistryEntryId,
        String visitorName,
        String visitorDocument,
        String visitorPhone,
        String block,
        String apartment,
        LocalDateTime visitedAt,
        String notes
) {}
