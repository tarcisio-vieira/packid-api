package com.packid.api.controller.registry.dto;

import com.packid.api.domain.model.RegistryEntry.EntryType;

import java.time.LocalDateTime;
import java.util.UUID;

public record RegistryEntryResponse(
        UUID id,
        UUID personId,
        UUID occupancyId,
        EntryType entryType,
        String name,
        String document,
        String phone,
        String email,
        String block,
        String apartment,
        String company,
        UUID serviceCompanyId,
        String serviceCompanyName,
        String ownerName,
        String brand,
        String model,
        String color,
        String identifier,
        String species,
        String breed,
        String parkingSpace,
        String notes,
        Boolean photoAvailable,
        Boolean photoOwnedByCurrentUser,
        String photoFileName,
        Boolean cpfPhotoAvailable,
        Boolean cpfPhotoOwnedByCurrentUser,
        String cpfPhotoFileName,
        Boolean rgPhotoAvailable,
        Boolean rgPhotoOwnedByCurrentUser,
        String rgPhotoFileName,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
