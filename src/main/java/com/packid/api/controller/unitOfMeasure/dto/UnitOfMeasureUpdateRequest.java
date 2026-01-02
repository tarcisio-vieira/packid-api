package com.packid.api.controller.unitOfMeasure.dto;

public record UnitOfMeasureUpdateRequest(
        String code,
        String name,
        String description,
        String symbol
) {}
