package com.packid.api.controller.packid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PackIdLabelCreateRequest(
        @NotBlank String packageCode,
        @NotBlank
        @Pattern(regexp = "(?:[1-9][0-9]{2}|1[0-2][0-9]{2})", message = "Apartamento inválido. Informe um apartamento entre o 1º e o 12º andar.")
        String apartment,
        @NotBlank
        @Pattern(regexp = "[1-4]", message = "Bloco inválido. Informe um bloco de 1 a 4.")
        String block,
        @NotBlank
        @Pattern(regexp = "(?:00[1-9]|0[1-9][0-9]|[1-9][0-9]{2})", message = "Página inválida. Informe uma página entre 001 e 999.")
        String bookPage
) {}
