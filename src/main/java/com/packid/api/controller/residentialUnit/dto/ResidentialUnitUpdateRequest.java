package com.packid.api.controller.residentialUnit.dto;

import java.util.UUID;

public record ResidentialUnitUpdateRequest(
        UUID condominiumId,
        String code,
        String name,
        String block,
        String apartment,
        Boolean active
) {}
