package com.packid.api.controller.person.dto;

import com.packid.api.domain.model.Person.PersonType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PersonCreateRequest(
        @NotNull UUID tenantId,
        @NotBlank String fullName,
        String document,
        @Email String email,
        String phone,
        @NotNull PersonType personType
) {}
