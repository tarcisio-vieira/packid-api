package com.packid.api.controller.resident.dto;

import jakarta.validation.constraints.NotBlank;

public record ResidentLoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {}
