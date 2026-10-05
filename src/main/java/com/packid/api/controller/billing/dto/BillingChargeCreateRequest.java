package com.packid.api.controller.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillingChargeCreateRequest(
        @NotBlank String block,
        @NotBlank String apartment,
        String bankProvider,
        String externalId,
        String referenceNumber,
        String description,
        @NotNull @Positive BigDecimal nominalValue,
        LocalDate issueDate,
        @NotNull LocalDate dueDate,
        String digitableLine,
        String barcode,
        String documentUrl
) {}
