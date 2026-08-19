package com.packid.api.controller.pool.dto;

public record PoolCardSettingsResponse(
        String condominiumName,
        boolean logoAvailable,
        String title,
        String subtitle,
        String openingHours,
        boolean showOpeningHours,
        String closedDaysMessage,
        boolean showClosedDays,
        int validityMonths,
        String validityMessage,
        boolean showValidityMessage,
        String generalInfo,
        boolean showGeneralInfo,
        String additionalInfo,
        String color
) {}
