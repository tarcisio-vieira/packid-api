package com.packid.api.controller.space.dto;

import com.packid.api.domain.model.SpaceAccessRequest.SpaceType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record SpaceManualReleaseRequest(
        @NotNull UUID residentRegistryEntryId,
        @NotNull SpaceType spaceType,
        LocalDateTime releasedAt
) {}
