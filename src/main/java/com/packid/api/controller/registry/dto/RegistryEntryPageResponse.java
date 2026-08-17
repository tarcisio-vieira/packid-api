package com.packid.api.controller.registry.dto;

import java.util.List;

public record RegistryEntryPageResponse(
        List<RegistryEntryResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
