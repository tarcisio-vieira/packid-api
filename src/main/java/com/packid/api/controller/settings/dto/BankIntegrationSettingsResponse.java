package com.packid.api.controller.settings.dto;

import java.time.LocalDateTime;

public record BankIntegrationSettingsResponse(
        String provider,
        String environment,
        boolean enabled,
        String clientId,
        boolean clientSecretConfigured,
        String apiBaseUrl,
        String tokenUrl,
        String workspaceId,
        String covenantCode,
        String beneficiaryCode,
        String notes,
        LocalDateTime lastSyncAt,
        String lastError
) {}
