package com.packid.api.controller.registry.dto;

import com.packid.api.controller.occupancy.dto.ApartmentOccupancyResponse;
import com.packid.api.controller.packid.dto.PackIdRecentResponse;

import java.util.List;

public record UnitRegistrySummaryResponse(
        String block,
        String apartment,
        ApartmentOccupancyResponse selectedOccupancy,
        List<ApartmentOccupancyResponse> occupancies,
        List<RegistryEntryResponse> residents,
        List<RegistryEntryResponse> bicycles,
        List<RegistryEntryResponse> vehicles,
        List<RegistryEntryResponse> pets,
        List<VisitorVisitResponse> visits,
        List<DeliveryRecordResponse> deliveries,
        List<PackIdRecentResponse> packIds
) {}
