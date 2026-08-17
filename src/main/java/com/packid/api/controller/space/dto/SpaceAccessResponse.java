package com.packid.api.controller.space.dto;

import com.packid.api.domain.model.SpaceAccessRequest.SpaceType;
import com.packid.api.domain.model.SpaceAccessRequest.Status;

import java.time.LocalDateTime;
import java.util.UUID;

public record SpaceAccessResponse(
        UUID id,
        UUID residentRegistryEntryId,
        String residentName,
        UUID occupancyId,
        String block,
        String apartment,
        SpaceType spaceType,
        Status status,
        LocalDateTime requestedAt,
        LocalDateTime releasedAt,
        LocalDateTime returnRequestedAt,
        LocalDateTime completedAt,
        String releasedBy,
        String completedBy,
        String notes
) {}
