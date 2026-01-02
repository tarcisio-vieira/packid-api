package com.packid.api.controller.tenant.dto;

public record TenantUpdateRequest(
        String name,
        String slug,
        Boolean active
) {}
