package com.packid.api.controller.registry.dto;

import com.packid.api.domain.model.RegistryEntry.EntryType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record RegistryEntryRequest(
        @NotNull EntryType entryType,
        @NotBlank String name,
        String document,
        String phone,
        @Email String email,
        Boolean unitOwner,
        LocalDate birthDate,
        String profession,
        Boolean pne,
        String block,
        String apartment,
        String company,
        UUID serviceCompanyId,
        String ownerName,
        String brand,
        String model,
        String color,
        String identifier,
        String species,
        String breed,
        String petSize,
        String parkingSpace,
        Boolean parkingSpaceRented,
        String parkingSpaceRentalNotes,
        String notes,
        Boolean active
) {}
