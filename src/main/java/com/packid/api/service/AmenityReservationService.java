package com.packid.api.service;

import com.packid.api.controller.amenity.dto.*;
import com.packid.api.domain.model.*;
import com.packid.api.domain.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AmenityReservationService {
    private final AmenitySpaceRepository spaceRepository;
    private final AmenityReservationRepository reservationRepository;
    private final AmenityReservationGuestRepository guestRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final ResidentSessionService residentSessionService;

    public AmenityReservationService(AmenitySpaceRepository spaceRepository,
                                     AmenityReservationRepository reservationRepository,
                                     AmenityReservationGuestRepository guestRepository,
                                     AuthenticatedUserService authenticatedUserService,
                                     AccessControlService accessControlService,
                                     ResidentSessionService residentSessionService) {
        this.spaceRepository = spaceRepository;
        this.reservationRepository = reservationRepository;
        this.guestRepository = guestRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.residentSessionService = residentSessionService;
    }

    public List<AmenitySpaceResponse> staffSpaces(OidcUser oidcUser) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireOperationalUser(user);
        return spaceRepository.findAllByTenantIdAndDeletedFalseOrderByNameAsc(user.getTenantId()).stream().map(this::toSpaceResponse).toList();
    }

    public List<AmenitySpaceResponse> residentSpaces(jakarta.servlet.http.HttpSession session) {
        var context = residentSessionService.requirePortalContext(session);
        return spaceRepository.findAllByTenantIdAndActiveTrueAndDeletedFalseOrderByNameAsc(context.tenant().getId()).stream().map(this::toSpaceResponse).toList();
    }

    @Transactional
    public AmenitySpaceResponse createSpace(OidcUser oidcUser, AmenitySpaceRequest request) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);
        AmenitySpace space = new AmenitySpace();
        space.setTenantId(user.getTenantId());
        apply(space, request);
        space.setCreatedBy(actor(user));
        return toSpaceResponse(spaceRepository.save(space));
    }

    @Transactional
    public AmenitySpaceResponse updateSpace(OidcUser oidcUser, UUID id, AmenitySpaceRequest request) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);
        AmenitySpace space = requireSpace(user.getTenantId(), id);
        apply(space, request);
        space.setUpdatedBy(actor(user));
        return toSpaceResponse(spaceRepository.save(space));
    }

    @Transactional
    public AmenitySpaceResponse uploadPhoto(OidcUser oidcUser, UUID id, MultipartFile file) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);
        AmenitySpace space = requireSpace(user.getTenantId(), id);
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma imagem.");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo precisa ser uma imagem.");
        if (file.getSize() > 5_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "A foto deve ter no máximo 5 MB.");
        try { space.setPhotoData(file.getBytes()); }
        catch (IOException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler a imagem."); }
        space.setPhotoMimeType(contentType);
        space.setPhotoFileName(file.getOriginalFilename());
        space.setUpdatedBy(actor(user));
        return toSpaceResponse(spaceRepository.save(space));
    }

    public AmenityPhoto photoStaff(OidcUser oidcUser, UUID id) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireOperationalUser(user);
        return photo(requireSpace(user.getTenantId(), id));
    }

    public AmenityPhoto photoResident(jakarta.servlet.http.HttpSession session, UUID id) {
        var context = residentSessionService.requirePortalContext(session);
        return photo(requireSpace(context.tenant().getId(), id));
    }

    public List<AmenityReservationResponse> staffReservations(OidcUser oidcUser, UUID spaceId, LocalDate from, LocalDate to,
                                                               AmenityReservation.Status status, boolean includePast) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireOperationalUser(user);
        LocalDate today = LocalDate.now();
        LocalDate effectiveFrom = !includePast && from == null ? today : from;
        Comparator<AmenityReservation> order = Comparator
                .comparing((AmenityReservation r) -> r.getReservationDate().isBefore(today) ? 1 : 0)
                .thenComparing(r -> r.getReservationDate().isBefore(today) ? r.getReservationDate().toEpochDay() * -1 : r.getReservationDate().toEpochDay());
        return reservationRepository.findAllByTenantIdAndDeletedFalseOrderByReservationDateAsc(user.getTenantId()).stream()
                .filter(r -> spaceId == null || spaceId.equals(r.getAmenitySpaceId()))
                .filter(r -> status == null || status == r.getStatus())
                .filter(r -> effectiveFrom == null || !r.getReservationDate().isBefore(effectiveFrom))
                .filter(r -> to == null || !r.getReservationDate().isAfter(to))
                .sorted(order)
                .map(r -> toReservationResponse(r, user.getTenantId())).toList();
    }

    public List<AmenityReservationResponse> residentReservations(jakarta.servlet.http.HttpSession session) {
        var context = residentSessionService.requirePortalContext(session);
        return reservationRepository.findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByReservationDateDesc(
                context.tenant().getId(), context.occupancy().getId()).stream()
                .map(r -> toReservationResponse(r, context.tenant().getId())).toList();
    }

    public List<LocalDate> residentBookedDates(jakarta.servlet.http.HttpSession session, UUID spaceId, LocalDate from, LocalDate to) {
        var context = residentSessionService.requirePortalContext(session);
        UUID tenantId = context.tenant().getId();
        requireSpace(tenantId, spaceId);
        if (from == null || to == null || to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Período de consulta inválido.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 400) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consulte no máximo 400 dias por vez.");
        }
        return reservationRepository
                .findAllByTenantIdAndAmenitySpaceIdAndStatusAndDeletedFalseAndReservationDateBetweenOrderByReservationDateAsc(
                        tenantId, spaceId, AmenityReservation.Status.BOOKED, from, to)
                .stream()
                .map(AmenityReservation::getReservationDate)
                .distinct()
                .toList();
    }

    @Transactional
    public AmenityReservationResponse reserve(jakarta.servlet.http.HttpSession session, AmenityReservationRequest request) {
        var context = residentSessionService.requirePortalContext(session);
        UUID tenantId = context.tenant().getId();
        AmenitySpace space = requireSpace(tenantId, request.amenitySpaceId());
        if (!Boolean.TRUE.equals(space.getActive())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este ambiente não está disponível para reservas.");

        LocalDate today = LocalDate.now();
        long advanceDays = ChronoUnit.DAYS.between(today, request.reservationDate());
        if (advanceDays < space.getMinAdvanceDays())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A reserva exige antecedência mínima de " + space.getMinAdvanceDays() + " dias.");
        if (advanceDays > space.getMaxAdvanceDays())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A reserva pode ser feita com no máximo " + space.getMaxAdvanceDays() + " dias de antecedência.");
        if (reservationRepository.existsByTenantIdAndAmenitySpaceIdAndReservationDateAndStatusAndDeletedFalse(
                tenantId, space.getId(), request.reservationDate(), AmenityReservation.Status.BOOKED))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este ambiente já está reservado para a data selecionada.");

        AmenityReservation r = new AmenityReservation();
        r.setTenantId(tenantId);
        r.setAmenitySpaceId(space.getId());
        r.setOccupancyId(context.occupancy().getId());
        r.setResidentRegistryEntryId(context.resident() == null ? null : context.resident().getId());
        r.setBlock(context.occupancy().getBlock());
        r.setApartment(context.occupancy().getApartment());
        r.setReservationDate(request.reservationDate());
        r.setStatus(AmenityReservation.Status.BOOKED);
        r.setUsageFeeSnapshot(space.getUsageFee() == null ? BigDecimal.ZERO : space.getUsageFee());
        r.setRequestedAt(LocalDateTime.now());
        r.setNotes(clean(request.notes()));
        r.setCreatedBy("morador:" + context.resident().getResidentUsername());
        r = reservationRepository.save(r);
        saveGuests(tenantId, r.getId(), request.guests(), r.getCreatedBy());
        return toReservationResponse(r, tenantId);
    }

    @Transactional
    public AmenityReservationResponse cancelResident(jakarta.servlet.http.HttpSession session, UUID id) {
        var context = residentSessionService.requirePortalContext(session);
        AmenityReservation r = reservationRepository.findByTenantIdAndIdAndDeletedFalse(context.tenant().getId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        if (!r.getOccupancyId().equals(context.occupancy().getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta reserva não pertence à sua unidade.");
        if (r.getStatus() != AmenityReservation.Status.BOOKED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta reserva já está cancelada.");
        AmenitySpace space = requireSpace(context.tenant().getId(), r.getAmenitySpaceId());
        long days = ChronoUnit.DAYS.between(LocalDate.now(), r.getReservationDate());
        if (days < space.getCancellationDays())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O cancelamento precisa ser feito com pelo menos " + space.getCancellationDays() + " dias de antecedência.");
        r.setStatus(AmenityReservation.Status.CANCELLED);
        r.setCancelledAt(LocalDateTime.now());
        r.setCancelledBy("morador:" + context.resident().getResidentUsername());
        r.setUpdatedBy(r.getCancelledBy());
        return toReservationResponse(reservationRepository.save(r), context.tenant().getId());
    }

    @Transactional
    public AmenityReservationResponse replaceGuests(jakarta.servlet.http.HttpSession session, UUID id, List<AmenityGuestRequest> guests) {
        var context = residentSessionService.requirePortalContext(session);
        AmenityReservation r = reservationRepository.findByTenantIdAndIdAndDeletedFalse(context.tenant().getId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva não encontrada."));
        if (!r.getOccupancyId().equals(context.occupancy().getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta reserva não pertence à sua unidade.");
        if (r.getStatus() != AmenityReservation.Status.BOOKED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível alterar convidados de uma reserva cancelada.");
        guestRepository.deleteAllByReservationId(r.getId());
        saveGuests(context.tenant().getId(), r.getId(), guests, "morador:" + context.resident().getResidentUsername());
        return toReservationResponse(r, context.tenant().getId());
    }

    private void saveGuests(UUID tenantId, UUID reservationId, List<AmenityGuestRequest> guests, String actor) {
        if (guests == null) return;
        guests.stream().filter(Objects::nonNull).limit(200).forEach(g -> {
            String name = clean(g.name());
            if (name == null) return;
            AmenityReservationGuest guest = new AmenityReservationGuest();
            guest.setTenantId(tenantId); guest.setReservationId(reservationId); guest.setName(name);
            guest.setDocument(clean(g.document())); guest.setCreatedBy(actor);
            guestRepository.save(guest);
        });
    }

    private void apply(AmenitySpace space, AmenitySpaceRequest r) {
        if (r.maxAdvanceDays() < r.minAdvanceDays()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A antecedência máxima não pode ser menor que a mínima.");
        space.setName(r.name().trim()); space.setDescription(clean(r.description()));
        space.setUsageFee(r.usageFee()); space.setMinAdvanceDays(r.minAdvanceDays());
        space.setMaxAdvanceDays(r.maxAdvanceDays()); space.setCancellationDays(r.cancellationDays());
        space.setActive(r.active() == null || r.active());
    }

    private AmenitySpace requireSpace(UUID tenantId, UUID id) {
        return spaceRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ambiente não encontrado."));
    }
    private AmenityPhoto photo(AmenitySpace s) {
        if (s.getPhotoData() == null || s.getPhotoData().length == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto não cadastrada.");
        return new AmenityPhoto(s.getPhotoData(), Optional.ofNullable(s.getPhotoMimeType()).orElse("image/jpeg"));
    }
    private AmenitySpaceResponse toSpaceResponse(AmenitySpace s) {
        return new AmenitySpaceResponse(s.getId(), s.getName(), s.getDescription(), s.getUsageFee(), s.getMinAdvanceDays(), s.getMaxAdvanceDays(), s.getCancellationDays(), Boolean.TRUE.equals(s.getActive()), s.getPhotoData() != null && s.getPhotoData().length > 0);
    }
    private AmenityReservationResponse toReservationResponse(AmenityReservation r, UUID tenantId) {
        AmenitySpace s = requireSpace(tenantId, r.getAmenitySpaceId());
        List<AmenityGuestResponse> guests = guestRepository.findAllByTenantIdAndReservationIdAndDeletedFalseOrderByNameAsc(tenantId, r.getId()).stream()
                .map(g -> new AmenityGuestResponse(g.getId(), g.getName(), g.getDocument())).toList();
        return new AmenityReservationResponse(r.getId(), r.getAmenitySpaceId(), s.getName(), r.getOccupancyId(), r.getBlock(), r.getApartment(), r.getReservationDate(), r.getStatus(), r.getUsageFeeSnapshot(), r.getRequestedAt(), r.getCancelledAt(), r.getCancelledBy(), r.getNotes(), guests);
    }
    private String actor(AppUser user) { return user.getEmail() == null ? "system" : user.getEmail(); }
    private String clean(String v) { if (v == null) return null; String x = v.trim(); return x.isBlank() ? null : x; }
    public record AmenityPhoto(byte[] bytes, String mimeType) {}
}
