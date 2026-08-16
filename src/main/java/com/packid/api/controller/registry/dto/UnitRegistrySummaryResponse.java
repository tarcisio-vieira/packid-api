package com.packid.api.controller.registry.dto;

import com.packid.api.controller.occupancy.dto.ApartmentOccupancyResponse;
import com.packid.api.controller.packid.dto.PackIdRecentResponse;
import com.packid.api.controller.servicerecord.dto.ServiceRecordResponse;

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
        List<ServiceRecordResponse> serviceRecords,
        List<PackIdRecentResponse> packIds
) {}
