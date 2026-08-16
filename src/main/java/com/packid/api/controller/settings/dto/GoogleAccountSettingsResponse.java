package com.packid.api.controller.settings.dto;

import java.time.LocalDateTime;

public record GoogleAccountSettingsResponse(
        boolean connected,
        String email,
        boolean driveEnabled,
        boolean gmailEnabled,
        LocalDateTime connectedAt,
        LocalDateTime lastRefreshAt,
        String lastError
) {}
