package com.packid.api.service;

import com.packid.api.controller.registry.dto.VisitorVisitRequest;
import com.packid.api.controller.registry.dto.VisitorVisitResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.VisitorVisit;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.repository.VisitorVisitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class VisitorVisitService {

    private final VisitorVisitRepository repository;
    private final RegistryEntryRepository registryRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public VisitorVisitService(
            VisitorVisitRepository repository,
            RegistryEntryRepository registryRepository,
            AuthenticatedUserService authenticatedUserService
    ) {
        this.repository = repository;
        this.registryRepository = registryRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public VisitorVisitResponse create(OidcUser oidcUser, VisitorVisitRequest request) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry visitor = requireVisitor(appUser.getTenantId(), request.visitorRegistryEntryId());

        VisitorVisit visit = new VisitorVisit();
        visit.setTenantId(appUser.getTenantId());
        visit.setVisitorRegistryEntryId(visitor.getId());
        visit.setBlock(required(request.block(), "Bloco é obrigatório."));
        visit.setApartment(required(request.apartment(), "Apartamento é obrigatório."));
        visit.setVisitedAt(request.visitedAt() == null ? LocalDateTime.now() : request.visitedAt());
        visit.setNotes(clean(request.notes()));
        visit.setCreatedBy(actor(appUser));

        return toResponse(repository.save(visit), visitor);
    }

    public List<VisitorVisitResponse> getByVisitor(OidcUser oidcUser, UUID visitorId) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry visitor = requireVisitor(appUser.getTenantId(), visitorId);
        return repository
                .findAllByTenantIdAndVisitorRegistryEntryIdAndDeletedFalseOrderByVisitedAtDesc(appUser.getTenantId(), visitorId)
                .stream()
                .map(item -> toResponse(item, visitor))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VisitorVisitResponse> getByUnit(AppUser appUser, String block, String apartment) {
        return getByUnit(appUser, block, apartment, null, null);
    }

    @Transactional(readOnly = true)
    public List<VisitorVisitResponse> getByUnit(
            AppUser appUser, String block, String apartment, LocalDateTime from, LocalDateTime to
    ) {
        String cleanedBlock = required(block, "Bloco é obrigatório.");
        String cleanedApartment = required(apartment, "Apartamento é obrigatório.");

        List<VisitorVisit> visits;
        if (from != null && to != null) {
            visits = repository.findByUnitBetween(appUser.getTenantId(), cleanedBlock, cleanedApartment, from, to);
        } else if (from != null) {
            visits = repository.findByUnitFrom(appUser.getTenantId(), cleanedBlock, cleanedApartment, from);
        } else if (to != null) {
            visits = repository.findByUnitUntil(appUser.getTenantId(), cleanedBlock, cleanedApartment, to);
        } else {
            visits = repository.findAllByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndDeletedFalseOrderByVisitedAtDesc(
                    appUser.getTenantId(), cleanedBlock, cleanedApartment);
        }

        return visits.stream()
                .map(item -> toResponse(item, item.getVisitor()))
                .toList();
    }

    private RegistryEntry requireVisitor(UUID tenantId, UUID id) {
        RegistryEntry visitor = registryRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visitante não encontrado."));
        if (visitor.getEntryType() != RegistryEntry.EntryType.VISITOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O cadastro informado não é de visitante.");
        }
        return visitor;
    }

    private VisitorVisitResponse toResponse(VisitorVisit visit, RegistryEntry visitor) {
        return new VisitorVisitResponse(
                visit.getId(),
                visit.getVisitorRegistryEntryId(),
                visitor == null ? null : visitor.getName(),
                visitor == null ? null : visitor.getDocument(),
                visitor == null ? null : visitor.getPhone(),
                visit.getBlock(),
                visit.getApartment(),
                visit.getVisitedAt(),
                visit.getNotes()
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
