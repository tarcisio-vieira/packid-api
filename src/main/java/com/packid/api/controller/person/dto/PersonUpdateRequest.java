package com.packid.api.controller.person.dto;

import com.packid.api.domain.model.Person.PersonType;
import jakarta.validation.constraints.Email;

public record PersonUpdateRequest(
        String fullName,
        String document,
        @Email String email,
        String phone,
        PersonType personType
) {}
