package com.packid.api.controller.space.dto;

import java.util.UUID;

public record SpaceKeyAvailabilityResponse(
        boolean available,
        UUID currentRequestId,
        String holderResidentName,
        String holderBlock,
        String holderApartment
) {}
