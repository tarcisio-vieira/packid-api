package com.packid.api.controller.resident.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ResidentProfileUpdateRequest(
        @Size(max = 40) String phone,
        @Email @Size(max = 255) String email,
        @Size(max = 120) String profession
) {}
