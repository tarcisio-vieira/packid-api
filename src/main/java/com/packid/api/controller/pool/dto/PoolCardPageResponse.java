package com.packid.api.controller.pool.dto;

import java.util.List;

public record PoolCardPageResponse(
        List<PoolCardResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
