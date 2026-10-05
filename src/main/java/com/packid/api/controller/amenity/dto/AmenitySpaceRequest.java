package com.packid.api.controller.amenity.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record AmenitySpaceRequest(
        @NotBlank @Size(max = 120) String name,
        String description,
        @NotNull @DecimalMin("0.00") BigDecimal usageFee,
        @NotNull @Min(0) Integer minAdvanceDays,
        @NotNull @Min(1) Integer maxAdvanceDays,
        @NotNull @Min(0) Integer cancellationDays,
        Boolean active
) {}
