package com.packid.api.controller.space.dto;

import java.util.List;

public record SpaceAccessPageResponse(
        List<SpaceAccessResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {}
