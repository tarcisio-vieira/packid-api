package com.packid.api.controller.settings.dto;

import java.util.UUID;

public record CondominiumSettingsResponse(
        UUID tenantId,
        String tenantSlug,
        UUID condominiumId,
        String name,
        String documentNumber,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String zipCode,
        String phone,
        String email,
        String managerName,
        String whatsapp,
        String notes,
        boolean emailNotificationsEnabled,
        boolean packIdPrintTwoLabels,
        GoogleAccountSettingsResponse googleAccount
) {}
