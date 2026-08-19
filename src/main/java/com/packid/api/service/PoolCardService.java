package com.packid.api.service;

import com.packid.api.controller.pool.dto.PoolCardPageResponse;
import com.packid.api.controller.pool.dto.PoolCardRequest;
import com.packid.api.controller.pool.dto.PoolCardResidentOptionResponse;
import com.packid.api.controller.pool.dto.PoolCardResponse;
import com.packid.api.controller.pool.dto.PoolCardSettingsResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.Condominium;
import com.packid.api.domain.model.PoolCard;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.PoolCardRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    /** Mantém compatibilidade com versões anteriores do frontend durante atualização/cache. */
    @Transactional
    public List<PoolCardResponse> listLegacy(OidcUser oidcUser, String search) {
        AppUser user = viewer(oidcUser);
        String q = clean(search);
        String normalizedSearch = q == null ? "" : q.toLowerCase();
        return repository.searchAll(user.getTenantId(), normalizedSearch).stream()
                .map(card -> toResponse(card, card.getResident()))
                .toList();
    }

    /**
     * Lista paginada no banco. A associação resident é carregada no mesmo SELECT
     * para evitar uma consulta adicional para cada carteirinha (N+1).
     */
    @Transactional
    public PoolCardPageResponse list(OidcUser oidcUser, String search, String expiryFilter, int page, int size) {
        AppUser user = viewer(oidcUser);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(5, Math.min(size, 50));
        String q = clean(search);
        String normalizedSearch = q == null ? "" : q.toLowerCase();
        LocalDate today = LocalDate.now();
        String filter = clean(expiryFilter);

        Page<PoolCard> result;
        if ("EXPIRED".equalsIgnoreCase(filter)) {
            result = repository.searchExpired(user.getTenantId(), normalizedSearch, today, PageRequest.of(safePage, safeSize));
        } else if ("WEEK".equalsIgnoreCase(filter)) {
            result = repository.searchExpiringBetween(user.getTenantId(), normalizedSearch, today, today.plusDays(7), PageRequest.of(safePage, safeSize));
        } else if ("MONTH".equalsIgnoreCase(filter)) {
            result = repository.searchExpiringBetween(user.getTenantId(), normalizedSearch, today, today.plusMonths(1), PageRequest.of(safePage, safeSize));
        } else {
            result = repository.searchPage(user.getTenantId(), normalizedSearch, PageRequest.of(safePage, safeSize));
        }

        List<PoolCardResponse> content = result.getContent().stream()
                .map(card -> toResponse(card, card.getResident()))
                .toList();

        return new PoolCardPageResponse(content, result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }

    /**
     * Busca leve usada apenas no select pesquisável do cadastro/edição.
     * Não utiliza RegistryEntryResponse, evitando consultas de ocupação e
     * carteirinha para cada condômino exibido no autocomplete.
     */
    @Transactional
    public List<PoolCardResidentOptionResponse> residentOptions(OidcUser oidcUser, String search, int limit) {
        AppUser user = manager(oidcUser);
        int safeLimit = Math.max(5, Math.min(limit, 30));
        String q = clean(search);
        String normalizedSearch = q == null ? "" : q.toLowerCase();

        return registryEntryRepository.searchActiveResidentOptions(
                        user.getTenantId(),
                        RegistryEntry.EntryType.RESIDENT,
                        normalizedSearch,
                        PageRequest.of(0, safeLimit)
                ).stream()
                .map(entry -> new PoolCardResidentOptionResponse(
                        entry.getId(), entry.getName(), entry.getBlock(), entry.getApartment()))
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
        card.setReviewStatus(PoolCard.ReviewStatus.PENDING_REVIEW);
        card.setCreatedBy(actor(user));
        return toResponse(repository.save(card), resident);
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
        return toResponse(repository.save(card), resident);
    }

    @Transactional
    public List<PoolCardResponse> pendingReviews(OidcUser oidcUser, int limit) {
        AppUser user = manager(oidcUser);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return repository.findAllByTenantIdAndReviewStatusAndDeletedFalseOrderByMedicalReportSubmittedAtAsc(
                        user.getTenantId(), PoolCard.ReviewStatus.PENDING_REVIEW, PageRequest.of(0, safeLimit))
                .stream().filter(card -> clean(card.getMedicalReportDriveFileId()) != null)
                .map(this::toResponse).toList();
    }

    @Transactional
    public PoolCardResponse approve(OidcUser oidcUser, UUID id, String notes) {
        AppUser user = manager(oidcUser);
        PoolCard card = require(user.getTenantId(), id);
        if (clean(card.getMedicalReportDriveFileId()) == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Anexe o laudo médico antes de validar a carteirinha.");
        }
        card.setReviewStatus(PoolCard.ReviewStatus.APPROVED);
        card.setValidatedAt(LocalDateTime.now());
        card.setValidatedBy(actor(user));
        card.setReviewNotes(clean(notes));
        card.setUpdatedBy(actor(user));
        return toResponse(repository.save(card));
    }

    @Transactional
    public PoolCardResponse reject(OidcUser oidcUser, UUID id, String notes) {
        AppUser user = manager(oidcUser);
        PoolCard card = require(user.getTenantId(), id);
        card.setReviewStatus(PoolCard.ReviewStatus.REJECTED);
        card.setValidatedAt(LocalDateTime.now());
        card.setValidatedBy(actor(user));
        card.setReviewNotes(clean(notes));
        card.setUpdatedBy(actor(user));
        return toResponse(repository.save(card));
    }

    @Transactional
    public PoolCard ensurePendingCardForResident(UUID tenantId, RegistryEntry resident, String actor) {
        PoolCard latest = repository.findFirstByTenantIdAndResidentRegistryEntryIdAndDeletedFalseOrderByIssueDateDescCreatedAtDesc(tenantId, resident.getId()).orElse(null);
        if (latest != null && latest.getReviewStatus() != PoolCard.ReviewStatus.APPROVED) {
            return latest;
        }
        Condominium condominium = brandingService.requireCondominium(tenantId);
        int months = validityMonths(condominium);
        LocalDate issueDate = LocalDate.now();
        PoolCard card = new PoolCard();
        card.setTenantId(tenantId);
        card.setResidentRegistryEntryId(resident.getId());
        card.setIssueDate(issueDate);
        card.setValidityMonths(months);
        card.setValidUntil(issueDate.plusMonths(months));
        card.setUnderTen(resident.getBirthDate() != null && resident.getBirthDate().plusYears(10).isAfter(issueDate));
        card.setReviewStatus(PoolCard.ReviewStatus.PENDING_REVIEW);
        card.setCreatedBy(actor == null || actor.isBlank() ? "morador" : actor);
        return repository.save(card);
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
        return repository.findFirstByTenantIdAndResidentRegistryEntryIdAndDeletedFalseOrderByIssueDateDescCreatedAtDesc(tenantId, residentEntryId)
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

    public PoolCardResponse toResponse(PoolCard card) {
        RegistryEntry resident = registryEntryRepository
                .findByTenantIdAndIdAndDeletedFalse(card.getTenantId(), card.getResidentRegistryEntryId())
                .orElse(null);
        return toResponse(card, resident);
    }

    private PoolCardResponse toResponse(PoolCard card, RegistryEntry resident) {
        boolean valid = card.getReviewStatus() == PoolCard.ReviewStatus.APPROVED
                && card.getValidUntil() != null && !card.getValidUntil().isBefore(LocalDate.now());
        return new PoolCardResponse(
                card.getId(), card.getResidentRegistryEntryId(),
                resident == null ? "Condômino" : resident.getName(),
                resident == null ? null : resident.getBlock(),
                resident == null ? null : resident.getApartment(),
                card.getIssueDate(), card.getValidityMonths() == null ? 0 : card.getValidityMonths(),
                card.getValidUntil(), Boolean.TRUE.equals(card.getUnderTen()), valid,
                clean(card.getMedicalReportDriveFileId()) != null, card.getMedicalReportFileName(),
                card.getReviewStatus() == null ? PoolCard.ReviewStatus.PENDING_REVIEW : card.getReviewStatus(),
                card.getMedicalReportSubmittedAt(), card.getValidatedAt(), card.getValidatedBy(), card.getReviewNotes(),
                card.getCreatedAt(), card.getUpdatedAt());
    }

    private int validityMonths(Condominium c) { return c.getPoolValidityMonths() == null ? 6 : Math.max(1, c.getPoolValidityMonths()); }
    private String actor(AppUser user) { return clean(user.getEmail()) == null ? "system" : user.getEmail().trim(); }
    private String defaultText(String value, String fallback) { String c = clean(value); return c == null ? fallback : c; }
    private String clean(String value) { if (value == null) return null; String c = value.trim(); return c.isBlank() ? null : c; }
}
