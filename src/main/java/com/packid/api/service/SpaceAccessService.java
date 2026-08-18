package com.packid.api.service;

import com.packid.api.controller.space.dto.SpaceAccessResponse;
import com.packid.api.controller.space.dto.SpaceKeyAvailabilityResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.SpaceAccessRequest;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.repository.SpaceAccessRequestRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class SpaceAccessService {
    private static final List<SpaceAccessRequest.Status> PENDING_STATUSES = List.of(
            SpaceAccessRequest.Status.REQUESTED_PICKUP,
            SpaceAccessRequest.Status.REQUESTED_RETURN
    );
    private static final List<SpaceAccessRequest.Status> ACTIVE_STATUSES = List.of(
            SpaceAccessRequest.Status.REQUESTED_PICKUP,
            SpaceAccessRequest.Status.IN_USE,
            SpaceAccessRequest.Status.REQUESTED_RETURN
    );
    private static final List<SpaceAccessRequest.Status> KEY_HELD_STATUSES = List.of(
            SpaceAccessRequest.Status.IN_USE,
            SpaceAccessRequest.Status.REQUESTED_RETURN
    );
    private static final DateTimeFormatter AUDIT_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SpaceAccessRequestRepository repository;
    private final RegistryEntryRepository registryEntryRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;

    public SpaceAccessService(
            SpaceAccessRequestRepository repository,
            RegistryEntryRepository registryEntryRepository,
            AuthenticatedUserService authenticatedUserService,
            AccessControlService accessControlService
    ) {
        this.repository = repository;
        this.registryEntryRepository = registryEntryRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
    }

    public List<SpaceAccessResponse> pending(OidcUser oidcUser) {
        AppUser user = operationalUser(oidcUser);
        return repository.findAllByTenantIdAndStatusInAndDeletedFalseOrderByRequestedAtAsc(
                        user.getTenantId(), PENDING_STATUSES)
                .stream().map(item -> toResponse(item, user.getTenantId())).toList();
    }

    public List<SpaceAccessResponse> report(
            OidcUser oidcUser,
            SpaceAccessRequest.SpaceType spaceType,
            LocalDate from,
            LocalDate to
    ) {
        AppUser user = operationalUser(oidcUser);
        LocalDateTime fromDateTime = from == null
                ? LocalDate.of(1900, 1, 1).atStartOfDay()
                : from.atStartOfDay();
        LocalDateTime toDateTime = to == null
                ? LocalDate.of(9999, 12, 31).atTime(23, 59, 59, 999_999_999)
                : to.plusDays(1).atStartOfDay();

        List<SpaceAccessRequest> requests = spaceType == null
                ? repository.reportAll(user.getTenantId(), fromDateTime, toDateTime)
                : repository.reportBySpaceType(user.getTenantId(), spaceType, fromDateTime, toDateTime);

        return requests.stream()
                .map(item -> toResponse(item, user.getTenantId()))
                .toList();
    }

    @Transactional
    public SpaceAccessResponse release(OidcUser oidcUser, UUID id) {
        AppUser user = operationalUser(oidcUser);
        SpaceAccessRequest request = requireRequest(user.getTenantId(), id);
        if (request.getStatus() != SpaceAccessRequest.Status.REQUESTED_PICKUP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A chave só pode ser liberada quando o morador estiver aguardando a retirada.");
        }

        SpaceAccessRequest holder = currentKeyHolder(user.getTenantId(), request.getSpaceType());
        if (holder != null && !holder.getId().equals(request.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    keyAlreadyWithUnitMessage(holder));
        }

        request.setStatus(SpaceAccessRequest.Status.IN_USE);
        request.setReleasedAt(LocalDateTime.now());
        request.setReleasedBy(actor(user));
        request.setUpdatedBy(actor(user));
        return toResponse(repository.save(request), user.getTenantId());
    }

    @Transactional
    public SpaceAccessResponse complete(OidcUser oidcUser, UUID id) {
        AppUser user = operationalUser(oidcUser);
        SpaceAccessRequest request = requireRequest(user.getTenantId(), id);
        if (request.getStatus() != SpaceAccessRequest.Status.REQUESTED_RETURN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A devolução só pode ser concluída após o morador solicitar a entrega da chave.");
        }
        request.setStatus(SpaceAccessRequest.Status.COMPLETED);
        request.setCompletedAt(LocalDateTime.now());
        request.setCompletedBy(actor(user));
        request.setUpdatedBy(actor(user));
        return toResponse(repository.save(request), user.getTenantId());
    }

    public SpaceKeyAvailabilityResponse residentAvailability(
            ResidentSessionService.ResidentContext context,
            SpaceAccessRequest.SpaceType spaceType
    ) {
        UUID tenantId = context.tenant().getId();
        SpaceAccessRequest holder = currentKeyHolder(tenantId, spaceType);
        if (holder == null || sameOccupancy(holder, context.occupancy().getId())) {
            return new SpaceKeyAvailabilityResponse(true, holder == null ? null : holder.getId(), null, null, null);
        }
        return new SpaceKeyAvailabilityResponse(
                false,
                holder.getId(),
                residentName(holder, tenantId),
                holder.getBlock(),
                holder.getApartment()
        );
    }

    @Transactional
    public SpaceAccessResponse residentToggle(
            ResidentSessionService.ResidentContext context,
            SpaceAccessRequest.SpaceType spaceType,
            boolean assumeResponsibility
    ) {
        RegistryEntry resident = context.resident();
        var occupancy = context.occupancy();
        UUID tenantId = context.tenant().getId();

        SpaceAccessRequest current = repository
                .findFirstByTenantIdAndOccupancyIdAndSpaceTypeAndStatusInAndDeletedFalseOrderByRequestedAtDesc(
                        tenantId, occupancy.getId(), spaceType, ACTIVE_STATUSES)
                .orElse(null);

        if (current == null) {
            SpaceAccessRequest holder = currentKeyHolder(tenantId, spaceType);
            if (holder != null && !sameOccupancy(holder, occupancy.getId())) {
                if (!assumeResponsibility) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, keyAlreadyWithUnitMessage(holder));
                }
                closeTransferredResponsibility(holder, occupancy.getBlock(), occupancy.getApartment(), occupancy.getResidentUsername());
                return toResponse(createTransferredRequest(context, spaceType, holder), tenantId);
            }
            return toResponse(createPickupRequest(context, spaceType), tenantId);
        }

        if (current.getStatus() == SpaceAccessRequest.Status.IN_USE) {
            current.setStatus(SpaceAccessRequest.Status.REQUESTED_RETURN);
            current.setReturnRequestedAt(LocalDateTime.now());
            current.setUpdatedBy(residentActor(occupancy.getResidentUsername()));
            return toResponse(repository.save(current), tenantId);
        }

        // Evita solicitações duplicadas causadas por clique repetido no celular.
        return toResponse(current, tenantId);
    }

    public List<SpaceAccessResponse> residentHistory(ResidentSessionService.ResidentContext context) {
        return repository.findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByRequestedAtDesc(
                        context.tenant().getId(), context.occupancy().getId())
                .stream().map(item -> toResponse(item, context.tenant().getId())).toList();
    }

    public List<SpaceAccessResponse> getByUnit(
            AppUser user,
            String block,
            String apartment,
            UUID occupancyId
    ) {
        accessControlService.requireOperationalUser(user);
        List<SpaceAccessRequest> requests = occupancyId == null
                ? repository.findByUnit(user.getTenantId(), block, apartment)
                : repository.findByUnitAndOccupancy(user.getTenantId(), block, apartment, occupancyId);

        return requests.stream()
                .map(item -> toResponse(item, user.getTenantId()))
                .toList();
    }

    private SpaceAccessRequest createPickupRequest(
            ResidentSessionService.ResidentContext context,
            SpaceAccessRequest.SpaceType spaceType
    ) {
        var occupancy = context.occupancy();
        SpaceAccessRequest request = baseResidentRequest(context, spaceType);
        request.setStatus(SpaceAccessRequest.Status.REQUESTED_PICKUP);
        request.setCreatedBy(residentActor(occupancy.getResidentUsername()));
        return repository.save(request);
    }

    private SpaceAccessRequest createTransferredRequest(
            ResidentSessionService.ResidentContext context,
            SpaceAccessRequest.SpaceType spaceType,
            SpaceAccessRequest previousHolder
    ) {
        var occupancy = context.occupancy();
        LocalDateTime now = LocalDateTime.now();
        SpaceAccessRequest request = baseResidentRequest(context, spaceType);
        request.setStatus(SpaceAccessRequest.Status.IN_USE);
        request.setReleasedAt(now);
        request.setReleasedBy("transferência entre unidades");
        request.setNotes("Responsabilidade assumida da unidade " + unitLabel(previousHolder) + ".");
        request.setCreatedBy(residentActor(occupancy.getResidentUsername()));
        return repository.save(request);
    }

    private SpaceAccessRequest baseResidentRequest(
            ResidentSessionService.ResidentContext context,
            SpaceAccessRequest.SpaceType spaceType
    ) {
        RegistryEntry resident = context.resident();
        var occupancy = context.occupancy();
        SpaceAccessRequest request = new SpaceAccessRequest();
        request.setTenantId(context.tenant().getId());
        request.setResidentRegistryEntryId(resident.getId());
        request.setOccupancyId(occupancy.getId());
        request.setBlock(requiredUnit(occupancy.getBlock(), "Bloco não definido para a ocupação."));
        request.setApartment(requiredUnit(occupancy.getApartment(), "Apartamento não definido para a ocupação."));
        request.setSpaceType(spaceType);
        request.setRequestedAt(LocalDateTime.now());
        return request;
    }

    private void closeTransferredResponsibility(
            SpaceAccessRequest holder,
            String newBlock,
            String newApartment,
            String newResidentUsername
    ) {
        LocalDateTime now = LocalDateTime.now();
        holder.setStatus(SpaceAccessRequest.Status.COMPLETED);
        holder.setCompletedAt(now);
        holder.setCompletedBy("transferência para Bloco " + cleanUnit(newBlock) + " Apto " + cleanUnit(newApartment));
        holder.setUpdatedBy(residentActor(newResidentUsername));
        holder.setNotes(appendNote(holder.getNotes(),
                "Responsabilidade da chave transferida para Bloco " + cleanUnit(newBlock) + " Apto " + cleanUnit(newApartment)
                        + " em " + AUDIT_DATE_TIME.format(now) + "."));
        repository.save(holder);
    }

    private SpaceAccessRequest currentKeyHolder(UUID tenantId, SpaceAccessRequest.SpaceType spaceType) {
        return repository.findFirstByTenantIdAndSpaceTypeAndStatusInAndDeletedFalseOrderByRequestedAtDesc(
                tenantId, spaceType, KEY_HELD_STATUSES).orElse(null);
    }

    private boolean sameOccupancy(SpaceAccessRequest request, UUID occupancyId) {
        return request.getOccupancyId() != null && request.getOccupancyId().equals(occupancyId);
    }

    private String keyAlreadyWithUnitMessage(SpaceAccessRequest holder) {
        return "A chave já está com o morador da unidade " + unitLabel(holder) + ".";
    }

    private String unitLabel(SpaceAccessRequest request) {
        return "Bloco " + cleanUnit(request.getBlock()) + " Apto " + cleanUnit(request.getApartment());
    }

    private String cleanUnit(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String appendNote(String current, String note) {
        if (current == null || current.isBlank()) return note;
        return current.trim() + "\n" + note;
    }

    private String residentActor(String username) {
        return "morador:" + (username == null || username.isBlank() ? "unidade" : username.trim());
    }

    private AppUser operationalUser(OidcUser oidcUser) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireOperationalUser(user);
        return user;
    }

    private SpaceAccessRequest requireRequest(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitação da área de lazer não encontrada."));
    }

    public SpaceAccessResponse toResponse(SpaceAccessRequest item, UUID tenantId) {
        return new SpaceAccessResponse(
                item.getId(),
                item.getResidentRegistryEntryId(),
                residentName(item, tenantId),
                item.getOccupancyId(),
                item.getBlock(),
                item.getApartment(),
                item.getSpaceType(),
                item.getStatus(),
                item.getRequestedAt(),
                item.getReleasedAt(),
                item.getReturnRequestedAt(),
                item.getCompletedAt(),
                item.getReleasedBy(),
                item.getCompletedBy(),
                item.getNotes()
        );
    }

    private String residentName(SpaceAccessRequest item, UUID tenantId) {
        return registryEntryRepository.findByTenantIdAndIdAndDeletedFalse(
                        tenantId, item.getResidentRegistryEntryId())
                .map(RegistryEntry::getName)
                .orElse("Morador");
    }

    private String actor(AppUser user) {
        String email = user.getEmail();
        return email == null || email.isBlank() ? "system" : email.trim();
    }

    private String requiredUnit(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
        return value.trim();
    }
}
