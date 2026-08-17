package com.packid.api.controller.resident.dto;

public record ResidentCredentialsUpdateRequest(
        String username,
        String newPassword
) {}
