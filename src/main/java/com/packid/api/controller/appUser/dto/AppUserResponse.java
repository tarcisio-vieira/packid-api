package com.packid.api.controller.appUser.dto;

import com.packid.api.domain.model.AppUser.AuthProvider;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppUserResponse(
        UUID id,
        UUID tenantId,
        UUID personId,
        String email,
        String fullName,
        AuthProvider provider,
        String providerSubject,
        String role,
        Boolean enabled,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
