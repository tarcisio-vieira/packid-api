package com.packid.api.controller.resident.dto;

import com.packid.api.controller.packid.dto.PackIdRecentResponse;
import com.packid.api.controller.registry.dto.RegistryEntryResponse;
import com.packid.api.controller.space.dto.SpaceAccessResponse;

import java.util.List;

public record ResidentPortalResponse(
        ResidentSessionResponse session,
        RegistryEntryResponse resident,
        List<RegistryEntryResponse> residents,
        List<RegistryEntryResponse> bicycles,
        List<RegistryEntryResponse> vehicles,
        List<RegistryEntryResponse> pets,
        List<PackIdRecentResponse> packIds,
        List<SpaceAccessResponse> spaceAccesses
) {}
