package com.packid.api.service;

import com.packid.api.controller.pool.dto.PoolCardRequest;
import com.packid.api.controller.pool.dto.PoolCardResponse;
import com.packid.api.controller.pool.dto.PoolCardSettingsResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.Condominium;
import com.packid.api.domain.model.PoolCard;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.PoolCardRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PoolCardService {
    private final PoolCardRepository repository;
    private final RegistryEntryRepository registryEntryRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final CondominiumBrandingService brandingService;

    public PoolCardService(PoolCardRepository repository,
                           RegistryEntryRepository registryEntryRepository,
                           AuthenticatedUserService authenticatedUserService,
                           AccessControlService accessControlService,
                           CondominiumBrandingService brandingService) {
        this.repository = repository;
        this.registryEntryRepository = registryEntryRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.brandingService = brandingService;
    }

    @Transactional
    public List<PoolCardResponse> list(OidcUser oidcUser, String search) {
        AppUser user = viewer(oidcUser);
        String q = clean(search);
        return repository.findAllByTenantIdAndDeletedFalseOrderByValidUntilDesc(user.getTenantId()).stream()
                .filter(card -> q == null || matches(card, q))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PoolCardResponse get(OidcUser oidcUser, UUID id) {
        AppUser user = viewer(oidcUser);
        return toResponse(require(user.getTenantId(), id));
    }

    public PoolCardSettingsResponse settings(OidcUser oidcUser) {
        AppUser user = viewer(oidcUser);
        return settingsForTenant(user.getTenantId());
    }

    @Transactional
    public PoolCardResponse create(OidcUser oidcUser, PoolCardRequest request) {
        AppUser user = manager(oidcUser);
        RegistryEntry resident = requireResident(user.getTenantId(), request.residentRegistryEntryId());
        Condominium condominium = brandingService.requireCondominium(user.getTenantId());
        int months = validityMonths(condominium);

        PoolCard card = new PoolCard();
        card.setTenantId(user.getTenantId());
        card.setResidentRegistryEntryId(resident.getId());
        card.setIssueDate(request.issueDate());
        card.setValidityMonths(months);
        card.setValidUntil(request.issueDate().plusMonths(months));
        card.setUnderTen(Boolean.TRUE.equals(request.underTen()));
        card.setCreatedBy(actor(user));
        return toResponse(repository.save(card));
    }

    @Transactional
    public PoolCardResponse update(OidcUser oidcUser, UUID id, PoolCardRequest request) {
        AppUser user = manager(oidcUser);
        PoolCard card = require(user.getTenantId(), id);
        RegistryEntry resident = requireResident(user.getTenantId(), request.residentRegistryEntryId());
        Condominium condominium = brandingService.requireCondominium(user.getTenantId());
        int months = validityMonths(condominium);

        card.setResidentRegistryEntryId(resident.getId());
        card.setIssueDate(request.issueDate());
        card.setValidityMonths(months);
        card.setValidUntil(request.issueDate().plusMonths(months));
        card.setUnderTen(Boolean.TRUE.equals(request.underTen()));
        card.setUpdatedBy(actor(user));
        return toResponse(repository.save(card));
    }

    @Transactional
    public void delete(OidcUser oidcUser, UUID id) {
        AppUser user = manager(oidcUser);
        PoolCard card = require(user.getTenantId(), id);
        card.setDeleted(true);
        card.setDeletedAt(LocalDateTime.now());
        card.setDeletedBy(actor(user));
        repository.save(card);
    }

    @Transactional
    public PoolCardResponse latestForResident(UUID tenantId, UUID residentEntryId) {
        return repository.findFirstByTenantIdAndResidentRegistryEntryIdAndDeletedFalseOrderByIssueDateDesc(tenantId, residentEntryId)
                .map(this::toResponse).orElse(null);
    }

    public PoolCard require(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carteirinha de piscina não encontrada."));
    }

    public PoolCardSettingsResponse settingsForTenant(UUID tenantId) {
        Condominium c = brandingService.requireCondominium(tenantId);
        return new PoolCardSettingsResponse(
                c.getName(), clean(c.getLogoDriveFileId()) != null,
                defaultText(c.getPoolCardTitle(), "PISCINA"),
                defaultText(c.getPoolCardSubtitle(), "USO DA PISCINA"),
                c.getPoolOpeningHours(), !Boolean.FALSE.equals(c.getPoolShowOpeningHours()),
                c.getPoolClosedDaysMessage(), !Boolean.FALSE.equals(c.getPoolShowClosedDays()),
                validityMonths(c), c.getPoolValidityMessage(), !Boolean.FALSE.equals(c.getPoolShowValidityMessage()),
                c.getPoolGeneralInfo(), !Boolean.FALSE.equals(c.getPoolShowGeneralInfo()),
                c.getPoolAdditionalInfo(), defaultText(c.getPoolCardColor(), "#0B5C2B"));
    }

    private AppUser viewer(OidcUser principal) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requirePoolCardViewer(user);
        return user;
    }

    private AppUser manager(OidcUser principal) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requirePoolCardManager(user);
        return user;
    }

    private RegistryEntry requireResident(UUID tenantId, UUID id) {
        RegistryEntry entry = registryEntryRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condômino não encontrado."));
        if (entry.getEntryType() != RegistryEntry.EntryType.RESIDENT || !Boolean.TRUE.equals(entry.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um condômino ativo.");
        }
        if (clean(entry.getBlock()) == null || clean(entry.getApartment()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O condômino precisa ter bloco e apartamento cadastrados.");
        }
        return entry;
    }

    private boolean matches(PoolCard card, String query) {
        RegistryEntry r = card.getResident();
        String haystack = String.join(" ",
                r == null ? "" : defaultText(r.getName(), ""),
                r == null ? "" : defaultText(r.getBlock(), ""),
                r == null ? "" : defaultText(r.getApartment(), ""));
        return haystack.toLowerCase().contains(query.toLowerCase());
    }

    public PoolCardResponse toResponse(PoolCard card) {
        RegistryEntry resident = registryEntryRepository
                .findByTenantIdAndIdAndDeletedFalse(card.getTenantId(), card.getResidentRegistryEntryId())
                .orElse(null);
        boolean valid = card.getValidUntil() != null && !card.getValidUntil().isBefore(LocalDate.now());
        return new PoolCardResponse(
                card.getId(), card.getResidentRegistryEntryId(),
                resident == null ? "Condômino" : resident.getName(),
                resident == null ? null : resident.getBlock(),
                resident == null ? null : resident.getApartment(),
                card.getIssueDate(), card.getValidityMonths() == null ? 0 : card.getValidityMonths(),
                card.getValidUntil(), Boolean.TRUE.equals(card.getUnderTen()), valid,
                clean(card.getMedicalReportDriveFileId()) != null, card.getMedicalReportFileName(),
                card.getCreatedAt(), card.getUpdatedAt());
    }

    private int validityMonths(Condominium c) { return c.getPoolValidityMonths() == null ? 6 : Math.max(1, c.getPoolValidityMonths()); }
    private String actor(AppUser user) { return clean(user.getEmail()) == null ? "system" : user.getEmail().trim(); }
    private String defaultText(String value, String fallback) { String c = clean(value); return c == null ? fallback : c; }
    private String clean(String value) { if (value == null) return null; String c = value.trim(); return c.isBlank() ? null : c; }
}
