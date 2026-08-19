package com.packid.api.controller.pool.dto;

import jakarta.validation.constraints.Size;

public record PoolCardReviewRequest(@Size(max = 500) String notes) {}
