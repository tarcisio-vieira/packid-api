package com.packid.api.controller.servicecompany.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ServiceCompanyRequest(
        @NotBlank String name,
        String tradeName,
        String documentNumber,
        String phone,
        @Email String email,
        String contactName,
        String addressLine,
        String city,
        String state,
        String zipCode,
        String notes,
        Boolean active
) {}
