package com.packid.api.controller.settings.dto;

public record RegistryPhotoSettingsResponse(
        boolean showServiceProviderPhoto,
        boolean showDeliveryPersonPhoto
) {}
