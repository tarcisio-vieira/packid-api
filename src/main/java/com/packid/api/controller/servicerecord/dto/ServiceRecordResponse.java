package com.packid.api.controller.servicerecord.dto;

import com.packid.api.domain.model.ServiceRecord.ServiceScope;

import java.time.LocalDateTime;
import java.util.UUID;

public record ServiceRecordResponse(
        UUID id,
        UUID serviceProviderRegistryEntryId,
        String serviceProviderName,
        UUID serviceCompanyId,
        String serviceCompanyName,
        ServiceScope serviceScope,
        String block,
        String apartment,
        LocalDateTime performedAt,
        String serviceDescription,
        String notes,
        String createdBy
) {}
