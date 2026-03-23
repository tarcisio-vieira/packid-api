package com.packid.api.controller.packid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PackIdLabelCreateRequest(
        @NotBlank String packageCode,
        @NotNull UUID residentialUnitId,
        @NotNull UUID residentPersonId
) {}