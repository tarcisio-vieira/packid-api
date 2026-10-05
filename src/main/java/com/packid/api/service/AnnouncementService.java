package com.packid.api.service;

import com.packid.api.controller.announcement.dto.AnnouncementRequest;
import com.packid.api.controller.announcement.dto.AnnouncementResponse;
import com.packid.api.domain.model.Announcement;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.AnnouncementRepository;
import com.packid.api.domain.repository.AppUserRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.repository.TenantRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AnnouncementService {
    private static final Pattern DANGEROUS_BLOCKS = Pattern.compile("(?is)<(script|iframe|object|embed|form|meta|link)[^>]*>.*?</\\1>|<(script|iframe|object|embed|form|meta|link)[^>]*/?>");
    private static final Pattern EVENT_ATTRIBUTES = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_URL = Pattern.compile("(?i)(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private final AnnouncementRepository announcementRepository;
    private final AppUserRepository appUserRepository;
    private final RegistryEntryRepository registryEntryRepository;
    private final TenantRepository tenantRepository;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final ResidentSessionService residentSessionService;
    private final AnnouncementEmailService emailService;

    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            AppUserRepository appUserRepository,
            RegistryEntryRepository registryEntryRepository,
            TenantRepository tenantRepository,
            AuthenticatedUserService authenticatedUserService,
            AccessControlService accessControlService,
            ResidentSessionService residentSessionService,
            AnnouncementEmailService emailService
    ) {
        this.announcementRepository = announcementRepository;
        this.appUserRepository = appUserRepository;
        this.registryEntryRepository = registryEntryRepository;
        this.tenantRepository = tenantRepository;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.residentSessionService = residentSessionService;
        this.emailService = emailService;
    }

    @Transactional
    public List<AnnouncementResponse> staffList(OidcUser oidcUser) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireOperationalUser(user);
        return announcementRepository.findAllByTenantIdAndDeletedFalseOrderByPublishedAtDesc(user.getTenantId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<AnnouncementResponse> residentList(HttpSession session) {
        var context = residentSessionService.requirePortalContext(session);
        return announcementRepository.findAllByTenantIdAndDeletedFalseOrderByPublishedAtDesc(context.tenant().getId())
                .stream().map(this::toResponse).toList();
    }

    public AnnouncementResponse publish(OidcUser oidcUser, AnnouncementRequest request) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);

        String title = cleanRequired(request.title());
        String bodyHtml = sanitizeHtml(request.bodyHtml());
        String bodyText = htmlToText(bodyHtml);
        if (bodyText.isBlank()) throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Digite o conteúdo do comunicado.");

        Set<String> recipients = recipientEmails(user.getTenantId());
        if (recipients.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Nenhum usuário ativo deste condomínio possui e-mail cadastrado.");
        }

        Announcement announcement = new Announcement();
        announcement.setTenantId(user.getTenantId());
        announcement.setTitle(title);
        announcement.setBodyHtml(bodyHtml);
        announcement.setBodyText(bodyText);
        announcement.setPublishedAt(LocalDateTime.now());
        announcement.setRecipientCount(recipients.size());
        announcement.setDeliveredCount(0);
        announcement.setFailedCount(0);
        announcement.setDeliveryStatus(Announcement.DeliveryStatus.PENDING);
        announcement.setCreatedBy(clean(user.getEmail()) != null ? user.getEmail() : user.getFullName());
        announcement = announcementRepository.save(announcement);

        String tenantName = tenantRepository.findByIdAndDeletedFalse(user.getTenantId()).map(t -> t.getName()).orElse("VSGI Condomínio");
        emailService.sendAsync(announcement.getId(), user.getTenantId(), tenantName, recipients);
        return toResponse(announcement);
    }

    private Set<String> recipientEmails(UUID tenantId) {
        Set<String> emails = new LinkedHashSet<>();
        appUserRepository.findAllByTenantIdAndDeletedFalse(tenantId).stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .map(AppUser::getEmail)
                .map(this::normalizeEmail)
                .filter(value -> value != null)
                .forEach(emails::add);

        registryEntryRepository.findAllByTenantIdAndEntryTypeAndDeletedFalseOrderByNameAsc(tenantId, RegistryEntry.EntryType.RESIDENT).stream()
                .filter(row -> Boolean.TRUE.equals(row.getActive()))
                .filter(row -> clean(row.getResidentUsername()) != null && clean(row.getResidentPasswordHash()) != null)
                .map(RegistryEntry::getEmail)
                .map(this::normalizeEmail)
                .filter(value -> value != null)
                .forEach(emails::add);
        return emails;
    }

    private AnnouncementResponse toResponse(Announcement row) {
        return new AnnouncementResponse(
                row.getId(), row.getTitle(), row.getBodyHtml(), row.getBodyText(), row.getPublishedAt(),
                row.getCreatedBy(), row.getRecipientCount(), row.getDeliveredCount(), row.getFailedCount(),
                row.getDeliveryStatus() == null ? "PENDING" : row.getDeliveryStatus().name());
    }

    private String sanitizeHtml(String value) {
        String html = cleanRequired(value);
        if (html.length() > 5_500_000) throw new ResponseStatusException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "O comunicado ficou muito grande. Reduza as imagens.");
        html = DANGEROUS_BLOCKS.matcher(html).replaceAll("");
        html = EVENT_ATTRIBUTES.matcher(html).replaceAll("");
        html = JS_URL.matcher(html).replaceAll("$1=\"#\"");
        return html;
    }

    private String htmlToText(String html) {
        return TAGS.matcher(html.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")).replaceAll(" ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("[ \\t]+", " ").replaceAll("\\n\\s+", "\n").trim();
    }

    private String normalizeEmail(String value) {
        String cleaned = clean(value);
        if (cleaned == null || !cleaned.contains("@")) return null;
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private String cleanRequired(String value) {
        String cleaned = clean(value);
        if (cleaned == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Preencha os campos obrigatórios.");
        return cleaned;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
