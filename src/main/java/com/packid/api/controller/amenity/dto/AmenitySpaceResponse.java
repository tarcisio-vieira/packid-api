package com.packid.api.controller.amenity.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AmenitySpaceResponse(
        UUID id, String name, String description, BigDecimal usageFee,
        Integer minAdvanceDays, Integer maxAdvanceDays, Integer cancellationDays,
        boolean active, boolean photoAvailable
) {}
