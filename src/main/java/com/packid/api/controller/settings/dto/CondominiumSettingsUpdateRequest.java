package com.packid.api.controller.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CondominiumSettingsUpdateRequest(
        @NotBlank(message = "Informe o nome do condomínio.")
        @Size(max = 150, message = "O nome do condomínio deve ter no máximo 150 caracteres.")
        String name,
        @Size(max = 30) String documentNumber,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 120) String city,
        @Size(max = 80) String state,
        @Size(max = 20) String zipCode,
        @Size(max = 30) String phone,
        @Email(message = "Informe um e-mail válido.") @Size(max = 160) String email,
        @Size(max = 160) String managerName,
        @Size(max = 30) String whatsapp,
        @Size(max = 1000) String notes,
        Boolean emailNotificationsEnabled,
        Boolean residentCredentialEmailsEnabled,
        Boolean packIdPrintTwoLabels,

        @Size(max = 80) String poolCardTitle,
        @Size(max = 120) String poolCardSubtitle,
        @Size(max = 300) String poolOpeningHours,
        Boolean poolShowOpeningHours,
        @Size(max = 400) String poolClosedDaysMessage,
        Boolean poolShowClosedDays,
        @Min(1) @Max(60) Integer poolValidityMonths,
        @Size(max = 500) String poolValidityMessage,
        Boolean poolShowValidityMessage,
        @Size(max = 500) String poolGeneralInfo,
        Boolean poolShowGeneralInfo,
        @Size(max = 160) String poolAdditionalInfo,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Informe a cor no formato hexadecimal, por exemplo #0B5C2B.")
        String poolCardColor
) {}
