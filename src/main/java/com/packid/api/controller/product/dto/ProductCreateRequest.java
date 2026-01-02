package com.packid.api.controller.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductCreateRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
        @NotNull UUID unitOfMeasureId
) {}
