package com.packid.api.controller.servicerecord.dto;

import com.packid.api.domain.model.ServiceRecord.ServiceScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record ServiceRecordRequest(
        @NotNull UUID serviceProviderRegistryEntryId,
        @NotNull ServiceScope serviceScope,
        String block,
        String apartment,
        LocalDateTime performedAt,
        @NotBlank String serviceDescription,
        String notes
) {}
