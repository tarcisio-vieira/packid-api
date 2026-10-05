package com.packid.api.controller.billing.dto;

import com.packid.api.domain.model.BillingCharge;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BillingChargeResponse(
        UUID id,
        UUID occupancyId,
        String block,
        String apartment,
        String bankProvider,
        String externalId,
        String referenceNumber,
        String description,
        BigDecimal nominalValue,
        LocalDate issueDate,
        LocalDate dueDate,
        BillingCharge.Status status,
        String digitableLine,
        String barcode,
        String documentUrl,
        LocalDateTime lastBankUpdateAt
) {}
