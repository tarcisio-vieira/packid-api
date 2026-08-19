package com.packid.api.controller.resident.dto;

import com.packid.api.controller.packid.dto.PackIdRecentResponse;
import com.packid.api.controller.registry.dto.RegistryEntryResponse;
import com.packid.api.controller.registry.dto.VisitorVisitResponse;
import com.packid.api.controller.registry.dto.DeliveryRecordResponse;
import com.packid.api.controller.servicerecord.dto.ServiceRecordResponse;
import com.packid.api.controller.space.dto.SpaceAccessResponse;
import com.packid.api.controller.pool.dto.PoolCardResponse;
import com.packid.api.controller.pool.dto.PoolCardSettingsResponse;

import java.util.List;

public record ResidentPortalResponse(
        ResidentSessionResponse session,
        RegistryEntryResponse resident,
        List<RegistryEntryResponse> residents,
        List<RegistryEntryResponse> bicycles,
        List<RegistryEntryResponse> vehicles,
        List<RegistryEntryResponse> pets,
        List<VisitorVisitResponse> visits,
        List<DeliveryRecordResponse> deliveries,
        List<ServiceRecordResponse> serviceRecords,
        List<PackIdRecentResponse> packIds,
        List<SpaceAccessResponse> spaceAccesses,
        List<PoolCardResponse> poolCards,
        PoolCardSettingsResponse poolCardSettings
) {}
