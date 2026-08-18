package com.packid.api.service;

import com.packid.api.controller.packid.dto.PackIdRecentResponse;
import com.packid.api.controller.registry.dto.RegistryEntryResponse;
import com.packid.api.controller.resident.dto.ResidentPortalResponse;
import com.packid.api.controller.resident.dto.ResidentProfileUpdateRequest;
import com.packid.api.domain.model.ApartmentOccupancy;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.PackIdRepository;
import com.packid.api.domain.repository.PersonRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.integration.google.TenantGoogleAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.transaction.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ResidentPortalService {
    private final ResidentSessionService residentSessionService;
    private final RegistryEntryRepository registryEntryRepository;
    private final PackIdRepository packIdRepository;
    private final PersonRepository personRepository;
    private final RegistryEntryService registryEntryService;
    private final VisitorVisitService visitorVisitService;
    private final DeliveryRecordService deliveryRecordService;
    private final ServiceRecordService serviceRecordService;
    private final SpaceAccessService spaceAccessService;
    private final TenantGoogleAccountService googleAccountService;
    private final GoogleDrivePhotoService googleDrivePhotoService;
    private final ImageCompressionService imageCompressionService;

    public ResidentPortalService(
            ResidentSessionService residentSessionService,
            RegistryEntryRepository registryEntryRepository,
            PackIdRepository packIdRepository,
            PersonRepository personRepository,
            RegistryEntryService registryEntryService,
            VisitorVisitService visitorVisitService,
            DeliveryRecordService deliveryRecordService,
            ServiceRecordService serviceRecordService,
            SpaceAccessService spaceAccessService,
            TenantGoogleAccountService googleAccountService,
            GoogleDrivePhotoService googleDrivePhotoService,
            ImageCompressionService imageCompressionService
    ) {
        this.residentSessionService = residentSessionService;
        this.registryEntryRepository = registryEntryRepository;
        this.packIdRepository = packIdRepository;
        this.personRepository = personRepository;
        this.registryEntryService = registryEntryService;
        this.visitorVisitService = visitorVisitService;
        this.deliveryRecordService = deliveryRecordService;
        this.serviceRecordService = serviceRecordService;
        this.spaceAccessService = spaceAccessService;
        this.googleAccountService = googleAccountService;
        this.googleDrivePhotoService = googleDrivePhotoService;
        this.imageCompressionService = imageCompressionService;
    }

    public ResidentPortalResponse portal(jakarta.servlet.http.HttpSession session) {
        ResidentSessionService.ResidentContext context = residentSessionService.requirePortalContext(session);
        RegistryEntry resident = context.resident();
        ApartmentOccupancy occupancy = context.occupancy();
        UUID tenantId = context.tenant().getId();

        List<RegistryEntry> entries = unitEntries(context);
        LocalDateTime from = occupancy.getStartDate() == null ? null : occupancy.getStartDate().atStartOfDay();
        LocalDateTime to = occupancy.getEndDate() == null ? null : occupancy.getEndDate().plusDays(1).atStartOfDay();

        LocalDateTime finalFrom = from;
        LocalDateTime finalTo = to;
        return new ResidentPortalResponse(
                residentSessionService.current(session),
                registryEntryService.toResidentResponse(resident, tenantId),
                byType(entries, RegistryEntry.EntryType.RESIDENT, tenantId),
                byType(entries, RegistryEntry.EntryType.BICYCLE, tenantId),
                byType(entries, RegistryEntry.EntryType.VEHICLE, tenantId),
                byType(entries, RegistryEntry.EntryType.PET, tenantId),
                visitorVisitService.getByUnit(tenantId, occupancy.getBlock(), occupancy.getApartment(), finalFrom, finalTo),
                deliveryRecordService.getByUnit(tenantId, occupancy.getBlock(), occupancy.getApartment(), finalFrom, finalTo),
                serviceRecordService.getByUnit(tenantId, occupancy.getBlock(), occupancy.getApartment(), finalFrom, finalTo),
                packIdRepository.findByUnit(
                                tenantId,
                                occupancy.getBlock(),
                                occupancy.getApartment(),
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
        ResidentSessionService.ResidentContext context = residentSessionService.requirePortalContext(session);
        RegistryEntry requested = registryEntryRepository
                .findByTenantIdAndIdAndDeletedFalse(context.tenant().getId(), entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));

        requireSameResidentUnit(context, requested);
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

    @Transactional
    public RegistryEntryResponse updateProfile(jakarta.servlet.http.HttpSession session, UUID entryId, ResidentProfileUpdateRequest request) {
        ResidentSessionService.ResidentContext context = residentSessionService.requirePortalContext(session);
        RegistryEntry entry = registryEntryRepository
                .findByTenantIdAndIdAndDeletedFalse(context.tenant().getId(), entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condômino não encontrado."));
        requireSameResidentUnit(context, entry);
        if (entry.getEntryType() != RegistryEntry.EntryType.RESIDENT || !Boolean.TRUE.equals(entry.getActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente dados de condôminos ativos podem ser atualizados pelo portal.");
        }
        // Campos estruturais (nome, documento, proprietário, nascimento, PNE, bloco/apto e ocupação)
        // permanecem exclusivos da administração/secretaria.
        String phone = clean(request.phone());
        String email = clean(request.email());
        if (email != null) {
            personRepository.findByTenantIdAndEmailAndDeletedFalse(context.tenant().getId(), email)
                    .filter(other -> entry.getPersonId() == null || !other.getId().equals(entry.getPersonId()))
                    .ifPresent(other -> { throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Este e-mail já está vinculado a outro condômino."); });
        }
        entry.setPhone(phone);
        entry.setEmail(email);
        entry.setProfession(clean(request.profession()));
        String actor = "morador:" + context.occupancy().getResidentUsername();
        entry.setUpdatedBy(actor);
        if (entry.getPersonId() != null) {
            personRepository.findByTenantIdAndIdAndDeletedFalse(context.tenant().getId(), entry.getPersonId())
                    .ifPresent(person -> {
                        person.setPhone(phone);
                        person.setEmail(email);
                        person.setUpdatedBy(actor);
                        personRepository.save(person);
                    });
        }
        RegistryEntry saved = registryEntryRepository.save(entry);
        return registryEntryService.toResidentResponse(saved, context.tenant().getId());
    }

    @Transactional
    public RegistryEntryResponse uploadProfilePhoto(jakarta.servlet.http.HttpSession session, UUID entryId, MultipartFile file) {
        ResidentSessionService.ResidentContext context = residentSessionService.requirePortalContext(session);
        RegistryEntry entry = registryEntryRepository
                .findByTenantIdAndIdAndDeletedFalse(context.tenant().getId(), entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condômino não encontrado."));
        requireSameResidentUnit(context, entry);
        if (entry.getEntryType() != RegistryEntry.EntryType.RESIDENT || !Boolean.TRUE.equals(entry.getActive())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Somente a foto de condômino ativo pode ser atualizada pelo portal.");
        }

        String officialEmail = googleAccountService.getOfficialEmail(context.tenant().getId());
        if (officialEmail == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "A conta Google oficial do condomínio precisa estar conectada para salvar a foto.");
        }
        ImageCompressionService.ProcessedImage processed = imageCompressionService.process(file);
        String token = googleAccountService.freshAccessToken(context.tenant().getId());
        String oldFileId = entry.getPhotoDriveFileId();
        GoogleDrivePhotoService.DriveFile uploaded = googleDrivePhotoService.uploadPhoto(
                token, context.tenant().getId(), entry.getId(), entry.getEntryType().name(),
                processed.fileName(), processed.mimeType(), processed.bytes(),
                context.occupancy().getBlock(), context.occupancy().getApartment());
        entry.setPhotoDriveFileId(uploaded.id());
        entry.setPhotoMimeType(processed.mimeType());
        entry.setPhotoFileName(processed.fileName());
        entry.setPhotoOwnerEmail(officialEmail);
        entry.setUpdatedBy("morador:" + context.occupancy().getResidentUsername());
        RegistryEntry saved = registryEntryRepository.save(entry);
        if (oldFileId != null && !oldFileId.equals(uploaded.id())) {
            try { googleDrivePhotoService.deletePhoto(token, oldFileId); } catch (Exception ignored) { }
        }
        return registryEntryService.toResidentResponse(saved, context.tenant().getId());
    }

    public com.packid.api.controller.space.dto.SpaceKeyAvailabilityResponse spaceAvailability(
            jakarta.servlet.http.HttpSession session,
            com.packid.api.domain.model.SpaceAccessRequest.SpaceType spaceType
    ) {
        return spaceAccessService.residentAvailability(residentSessionService.requirePortalContext(session), spaceType);
    }

    public com.packid.api.controller.space.dto.SpaceAccessResponse toggleSpace(
            jakarta.servlet.http.HttpSession session,
            com.packid.api.domain.model.SpaceAccessRequest.SpaceType spaceType,
            boolean assumeResponsibility
    ) {
        return spaceAccessService.residentToggle(
                residentSessionService.requirePortalContext(session), spaceType, assumeResponsibility);
    }

    public List<com.packid.api.controller.space.dto.SpaceAccessResponse> spaces(jakarta.servlet.http.HttpSession session) {
        return spaceAccessService.residentHistory(residentSessionService.requirePortalContext(session));
    }

    private List<RegistryEntry> unitEntries(ResidentSessionService.ResidentContext context) {
        return registryEntryRepository.findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByNameAsc(
                        context.tenant().getId(), context.occupancy().getId())
                .stream().filter(item -> Boolean.TRUE.equals(item.getActive())).toList();
    }

    private List<RegistryEntryResponse> byType(List<RegistryEntry> entries, RegistryEntry.EntryType type, UUID tenantId) {
        return entries.stream()
                .filter(item -> item.getEntryType() == type)
                .map(item -> registryEntryService.toResidentResponse(item, tenantId))
                .toList();
    }

    private void requireSameResidentUnit(ResidentSessionService.ResidentContext context, RegistryEntry requested) {
        if (!context.occupancy().getId().equals(requested.getOccupancyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cadastro não pertence à unidade deste acesso.");
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
