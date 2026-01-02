package com.packid.api.controller.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record TenantCreateRequest(
        @NotBlank String name,
        @NotBlank String slug,
        Boolean active
) {}
