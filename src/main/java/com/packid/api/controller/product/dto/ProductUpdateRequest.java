package com.packid.api.controller.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductUpdateRequest(
        @Size(max = 50) String code,
        @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
        UUID unitOfMeasureId
) {}
