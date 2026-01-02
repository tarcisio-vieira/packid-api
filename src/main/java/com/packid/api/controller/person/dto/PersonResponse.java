package com.packid.api.controller.person.dto;

import com.packid.api.domain.model.Person.PersonType;

import java.time.LocalDateTime;
import java.util.UUID;

public record PersonResponse(
        UUID id,
        UUID tenantId,
        String fullName,
        String document,
        String email,
        String phone,
        PersonType personType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
