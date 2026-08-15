package com.packid.api.service;

import com.packid.api.controller.occupancy.dto.ApartmentOccupancyEndRequest;
import com.packid.api.controller.occupancy.dto.ApartmentOccupancyResponse;
import com.packid.api.controller.occupancy.dto.ApartmentOccupancyStartRequest;
import com.packid.api.domain.model.ApartmentOccupancy;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.ApartmentOccupancyRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ApartmentOccupancyService {

    private final ApartmentOccupancyRepository repository;
    private final RegistryEntryRepository registryEntryRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public ApartmentOccupancyService(
            ApartmentOccupancyRepository repository,
            RegistryEntryRepository registryEntryRepository,
            AuthenticatedUserService authenticatedUserService
    ) {
        this.repository = repository;
        this.registryEntryRepository = registryEntryRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    public List<ApartmentOccupancyResponse> list(OidcUser oidcUser, String block, String apartment) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        return listByUnit(appUser, required(block, "Bloco é obrigatório."), required(apartment, "Apartamento é obrigatório."))
                .stream().map(this::toResponse).toList();
    }

    public List<ApartmentOccupancy> listByUnit(AppUser appUser, String block, String apartment) {
        return repository.findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByStartDateDescCreatedAtDesc(
                appUser.getTenantId(), block, apartment);
    }

    public ApartmentOccupancy findById(AppUser appUser, UUID occupancyId) {
        return repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), occupancyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ocupação não encontrada."));
    }

    public ApartmentOccupancy findActive(AppUser appUser, String block, String apartment) {
        return repository.findFirstByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndStatusAndDeletedFalse(
                appUser.getTenantId(), block, apartment, ApartmentOccupancy.Status.ACTIVE).orElse(null);
    }

    @Transactional
    public ApartmentOccupancyResponse start(OidcUser oidcUser, ApartmentOccupancyStartRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        ApartmentOccupancy created = startInternal(
                appUser,
                required(request.block(), "Bloco é obrigatório."),
                required(request.apartment(), "Apartamento é obrigatório."),
                request.startDate() == null ? LocalDate.now() : request.startDate(),
                clean(request.notes())
        );
        return toResponse(created);
    }

    @Transactional
    public ApartmentOccupancy ensureActiveOccupancy(AppUser appUser, String block, String apartment) {
        String cleanedBlock = required(block, "Bloco é obrigatório.");
        String cleanedApartment = required(apartment, "Apartamento é obrigatório.");
        ApartmentOccupancy active = findActive(appUser, cleanedBlock, cleanedApartment);
        if (active != null) return active;
        return startInternal(appUser, cleanedBlock, cleanedApartment, LocalDate.now(), null);
    }

    @Transactional
    public ApartmentOccupancyResponse end(OidcUser oidcUser, ApartmentOccupancyEndRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        String block = required(request.block(), "Bloco é obrigatório.");
        String apartment = required(request.apartment(), "Apartamento é obrigatório.");
        ApartmentOccupancy occupancy = findActive(appUser, block, apartment);
        if (occupancy == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não existe ocupação ativa para esta unidade.");
        }

        LocalDate endDate = request.endDate() == null ? LocalDate.now() : request.endDate();
        if (endDate.isBefore(occupancy.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data de saída não pode ser anterior à data de entrada.");
        }
        if (endDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Para encerrar agora, a data de saída não pode estar no futuro.");
        }

        occupancy.setEndDate(endDate);
        occupancy.setStatus(ApartmentOccupancy.Status.ENDED);
        occupancy.setUpdatedBy(actor(appUser));
        ApartmentOccupancy saved = repository.save(occupancy);

        List<RegistryEntry> entries = registryEntryRepository
                .findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByNameAsc(appUser.getTenantId(), occupancy.getId());
        for (RegistryEntry entry : entries) {
            if (isOccupancyManagedType(entry.getEntryType()) && Boolean.TRUE.equals(entry.getActive())) {
                entry.setActive(false);
                entry.setUpdatedBy(actor(appUser));
            }
        }
        registryEntryRepository.saveAll(entries);
        return toResponse(saved);
    }

    private ApartmentOccupancy startInternal(
            AppUser appUser,
            String block,
            String apartment,
            LocalDate startDate,
            String notes
    ) {
        ApartmentOccupancy active = findActive(appUser, block, apartment);
        if (active != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma ocupação ativa para esta unidade.");
        }
        if (startDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data de entrada não pode estar no futuro.");
        }

        LocalDate latestEndDate = repository
                .findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByStartDateDescCreatedAtDesc(
                        appUser.getTenantId(), block, apartment)
                .stream()
                .filter(item -> item.getStatus() == ApartmentOccupancy.Status.ENDED && item.getEndDate() != null)
                .map(ApartmentOccupancy::getEndDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latestEndDate != null && !startDate.isAfter(latestEndDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A nova ocupação deve começar depois do encerramento da ocupação anterior (" + latestEndDate + ")."
            );
        }

        ApartmentOccupancy occupancy = new ApartmentOccupancy();
        occupancy.setTenantId(appUser.getTenantId());
        occupancy.setBlock(block);
        occupancy.setApartment(apartment);
        occupancy.setStartDate(startDate);
        occupancy.setStatus(ApartmentOccupancy.Status.ACTIVE);
        occupancy.setNotes(notes);
        occupancy.setCreatedBy(actor(appUser));
        return repository.save(occupancy);
    }

    public boolean isOccupancyManagedType(RegistryEntry.EntryType type) {
        return type == RegistryEntry.EntryType.RESIDENT
                || type == RegistryEntry.EntryType.BICYCLE
                || type == RegistryEntry.EntryType.PET
                || type == RegistryEntry.EntryType.VEHICLE;
    }

    public ApartmentOccupancyResponse toResponse(ApartmentOccupancy occupancy) {
        if (occupancy == null) return null;
        return new ApartmentOccupancyResponse(
                occupancy.getId(),
                occupancy.getBlock(),
                occupancy.getApartment(),
                occupancy.getStartDate(),
                occupancy.getEndDate(),
                occupancy.getStatus(),
                occupancy.getNotes()
        );
    }

    private String actor(AppUser appUser) {
        return appUser.getEmail() == null || appUser.getEmail().isBlank() ? "system" : appUser.getEmail().trim();
    }

    private String required(String value, String message) {
        String cleaned = clean(value);
        if (cleaned == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return cleaned;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
