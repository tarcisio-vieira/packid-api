package com.packid.api.controller.servicecompany.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record ServiceCompanyResponse(
        UUID id,
        String name,
        String tradeName,
        String documentNumber,
        String phone,
        String email,
        String contactName,
        String addressLine,
        String city,
        String state,
        String zipCode,
        String notes,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
