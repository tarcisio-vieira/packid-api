package com.packid.api.controller.document.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ManagedDocumentResponse(
        UUID id,
        String category,
        String displayName,
        String originalFileName,
        String mimeType,
        String fileExtension,
        long fileSize,
        LocalDateTime createdAt,
        String createdBy
) {}
