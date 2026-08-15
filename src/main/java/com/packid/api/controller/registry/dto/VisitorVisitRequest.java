package com.packid.api.controller.registry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record VisitorVisitRequest(
        @NotNull UUID visitorRegistryEntryId,
        @NotBlank String block,
        @NotBlank String apartment,
        LocalDateTime visitedAt,
        String notes
) {}
