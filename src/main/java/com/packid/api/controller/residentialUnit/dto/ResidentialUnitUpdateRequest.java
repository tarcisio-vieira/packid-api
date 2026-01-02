package com.packid.api.controller.residentialUnit.dto;

import java.util.UUID;

public record ResidentialUnitUpdateRequest(
        UUID condominiumId,
        String code,
        String name,
        Boolean active
) {}
