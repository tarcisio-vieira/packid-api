package com.packid.api.controller.condominium.dto;

public record CondominiumUpdateRequest(
        String name,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zipCode
) {}
