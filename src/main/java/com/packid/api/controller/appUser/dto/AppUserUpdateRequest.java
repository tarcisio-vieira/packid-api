package com.packid.api.controller.appUser.dto;

import com.packid.api.domain.model.AppUser.AuthProvider;
import jakarta.validation.constraints.Email;

import java.util.UUID;

public record AppUserUpdateRequest(

        UUID personId,

        @Email
        String email,

        String fullName,

        AuthProvider provider,

        String providerSubject,

        String role,

        Boolean enabled
) {}
