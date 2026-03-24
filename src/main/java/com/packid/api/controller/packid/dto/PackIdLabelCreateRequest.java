package com.packid.api.controller.packid.dto;

import jakarta.validation.constraints.NotBlank;

public record PackIdLabelCreateRequest(
        @NotBlank String packageCode,
        @NotBlank String apartment
) {}