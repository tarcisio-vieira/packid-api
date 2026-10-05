package com.packid.api.controller.settings.dto;

public record BankIntegrationSettingsRequest(
        String provider,
        String environment,
        Boolean enabled,
        String clientId,
        String clientSecret,
        String apiBaseUrl,
        String tokenUrl,
        String workspaceId,
        String covenantCode,
        String beneficiaryCode,
        String notes
) {}
