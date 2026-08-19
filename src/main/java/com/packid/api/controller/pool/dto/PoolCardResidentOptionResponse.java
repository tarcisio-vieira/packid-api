package com.packid.api.controller.pool.dto;

import java.util.UUID;

public record PoolCardResidentOptionResponse(
        UUID id,
        String name,
        String block,
        String apartment
) {
}
