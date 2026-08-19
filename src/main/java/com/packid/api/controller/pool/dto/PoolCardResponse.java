package com.packid.api.controller.pool.dto;

import com.packid.api.domain.model.PoolCard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PoolCardResponse(
        UUID id,
        UUID residentRegistryEntryId,
        String residentName,
        String block,
        String apartment,
        LocalDate issueDate,
        int validityMonths,
        LocalDate validUntil,
        boolean underTen,
        boolean valid,
        boolean medicalReportAvailable,
        String medicalReportFileName,
        PoolCard.ReviewStatus reviewStatus,
        LocalDateTime medicalReportSubmittedAt,
        LocalDateTime validatedAt,
        String validatedBy,
        String reviewNotes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
