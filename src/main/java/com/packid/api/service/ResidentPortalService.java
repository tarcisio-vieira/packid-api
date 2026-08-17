package com.packid.api.service;

import com.packid.api.controller.packid.dto.PackIdRecentResponse;
import com.packid.api.controller.registry.dto.RegistryEntryResponse;
import com.packid.api.controller.resident.dto.ResidentPortalResponse;
import com.packid.api.domain.model.ApartmentOccupancy;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.ApartmentOccupancyRepository;
import com.packid.api.domain.repository.PackIdRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.integration.google.TenantGoogleAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ResidentPortalService {
    private final ResidentSessionService residentSessionService;
    private final RegistryEntryRepository registryEntryRepository;
    private final ApartmentOccupancyRepository occupancyRepository;
    private final PackIdRepository packIdRepository;
    private final RegistryEntryService registryEntryService;
    private final SpaceAccessService spaceAccessService;
    private final TenantGoogleAccountService googleAccountService;
    private final GoogleDrivePhotoService googleDrivePhotoService;

    public ResidentPortalService(
            ResidentSessionService residentSessionService,
            RegistryEntryRepository registryEntryRepository,
            ApartmentOccupancyRepository occupancyRepository,
            PackIdRepository packIdRepository,
            RegistryEntryService registryEntryService,
            SpaceAccessService spaceAccessService,
            TenantGoogleAccountService googleAccountService,
            GoogleDrivePhotoService googleDrivePhotoService
    ) {
        this.residentSessionService = residentSessionService;
        this.registryEntryRepository = registryEntryRepository;
        this.occupancyRepository = occupancyRepository;
        this.packIdRepository = packIdRepository;
        this.registryEntryService = registryEntryService;
        this.spaceAccessService = spaceAccessService;
        this.googleAccountService = googleAccountService;
        this.googleDrivePhotoService = googleDrivePhotoService;
    }

    public ResidentPortalResponse portal(jakarta.servlet.http.HttpSession session) {
        ResidentSessionService.ResidentContext context = residentSessionService.requireContext(session);
        RegistryEntry resident = context.resident();
        UUID tenantId = context.tenant().getId();

        List<RegistryEntry> entries = unitEntries(context);
        LocalDateTime from = null;
        LocalDateTime to = null;
        if (resident.getOccupancyId() != null) {
            ApartmentOccupancy occupancy = occupancyRepository
                    .findByTenantIdAndIdAndDeletedFalse(tenantId, resident.getOccupancyId())
                    .orElse(null);
            if (occupancy != null) {
                from = occupancy.getStartDate() == null ? null : occupancy.getStartDate().atStartOfDay();
                to = occupancy.getEndDate() == null ? null : occupancy.getEndDate().plusDays(1).atStartOfDay();
            }
        }

        LocalDateTime finalFrom = from;
        LocalDateTime finalTo = to;
        return new ResidentPortalResponse(
                residentSessionService.current(session),
                registryEntryService.toResidentResponse(resident, tenantId),
                byType(entries, RegistryEntry.EntryType.RESIDENT, tenantId),
                byType(entries, RegistryEntry.EntryType.BICYCLE, tenantId),
                byType(entries, RegistryEntry.EntryType.VEHICLE, tenantId),
                byType(entries, RegistryEntry.EntryType.PET, tenantId),
                packIdRepository.findByUnit(
                                tenantId,
                                resident.getBlock(),
                                resident.getApartment(),
                                finalFrom == null ? null : Timestamp.valueOf(finalFrom),
                                finalTo == null ? null : Timestamp.valueOf(finalTo),
                                200
                        ).stream()
                        .map(r -> new PackIdRecentResponse(
                                r.getId(), r.getBookPage(), r.getBlock(), r.getApartment(),
                                r.getResidentFullName(), r.getPackageCode(), r.getLabelPackageCode(),
                                r.getObservations(), r.getArrivedAt(), r.getCreatedBy()))
                        .toList(),
                spaceAccessService.residentHistory(context)
        );
    }

    public GoogleDrivePhotoService.PhotoContent photo(jakarta.servlet.http.HttpSession session, UUID entryId) {
        ResidentSessionService.ResidentContext context = residentSessionService.requireContext(session);
        RegistryEntry requested = registryEntryRepository
                .findByTenantIdAndIdAndDeletedFalse(context.tenant().getId(), entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        requireSameResidentUnit(context.resident(), requested);
        if (!Boolean.TRUE.equals(requested.getActive()) && requested.getOccupancyId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cadastro não disponível para esta unidade.");
        }
        if (clean(requested.getPhotoDriveFileId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Este cadastro não possui foto.");
        }

        String officialEmail = googleAccountService.getOfficialEmail(context.tenant().getId());
        if (officialEmail == null || !sameEmail(officialEmail, requested.getPhotoOwnerEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A foto não está armazenada na conta oficial do condomínio.");
        }

        return googleDrivePhotoService.downloadPhoto(
                googleAccountService.freshAccessToken(context.tenant().getId()),
                requested.getPhotoDriveFileId(),
                requested.getPhotoMimeType()
        );
    }

    public com.packid.api.controller.space.dto.SpaceAccessResponse toggleSpace(
            jakarta.servlet.http.HttpSession session,
            com.packid.api.domain.model.SpaceAccessRequest.SpaceType spaceType
    ) {
        return spaceAccessService.residentToggle(residentSessionService.requireContext(session), spaceType);
    }

    public List<com.packid.api.controller.space.dto.SpaceAccessResponse> spaces(jakarta.servlet.http.HttpSession session) {
        return spaceAccessService.residentHistory(residentSessionService.requireContext(session));
    }

    private List<RegistryEntry> unitEntries(ResidentSessionService.ResidentContext context) {
        RegistryEntry resident = context.resident();
        UUID tenantId = context.tenant().getId();
        if (resident.getOccupancyId() != null) {
            return registryEntryRepository.findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByNameAsc(
                            tenantId, resident.getOccupancyId())
                    .stream().filter(item -> Boolean.TRUE.equals(item.getActive())).toList();
        }
        return registryEntryRepository
                .findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndActiveTrueAndDeletedFalseOrderByNameAsc(
                        tenantId, resident.getBlock(), resident.getApartment());
    }

    private List<RegistryEntryResponse> byType(List<RegistryEntry> entries, RegistryEntry.EntryType type, UUID tenantId) {
        return entries.stream()
                .filter(item -> item.getEntryType() == type)
                .map(item -> registryEntryService.toResidentResponse(item, tenantId))
                .toList();
    }

    private void requireSameResidentUnit(RegistryEntry resident, RegistryEntry requested) {
        if (resident.getOccupancyId() != null) {
            if (!resident.getOccupancyId().equals(requested.getOccupancyId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cadastro não pertence ao acesso deste morador.");
            }
            return;
        }
        if (!same(resident.getBlock(), requested.getBlock()) || !same(resident.getApartment(), requested.getApartment())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cadastro não pertence à unidade deste morador.");
        }
    }

    private boolean sameEmail(String left, String right) {
        return same(left, right);
    }

    private boolean same(String left, String right) {
        String a = clean(left), b = clean(right);
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
