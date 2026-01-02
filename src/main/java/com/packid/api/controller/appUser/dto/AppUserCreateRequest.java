package com.packid.api.controller.appUser.dto;

import com.packid.api.domain.model.AppUser.AuthProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record AppUserCreateRequest(

        UUID personId,

        @NotBlank
        @Email
        String email,

        String fullName,

        AuthProvider provider,

        @NotBlank
        String providerSubject,

        @NotBlank
        String role,

        Boolean enabled
) {}
