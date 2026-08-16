package com.packid.api.service.notification;

import com.packid.api.domain.repository.CondominiumRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UnitChangeNotificationPublisher {

    private final RegistryEntryRepository registryEntryRepository;
    private final CondominiumRepository condominiumRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public UnitChangeNotificationPublisher(
            RegistryEntryRepository registryEntryRepository,
            CondominiumRepository condominiumRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.registryEntryRepository = registryEntryRepository;
        this.condominiumRepository = condominiumRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public List<String> residentEmails(UUID tenantId, String block, String apartment, Collection<String> extras) {
        LinkedHashSet<String> emails = new LinkedHashSet<>();
        if (tenantId != null && clean(block) != null && clean(apartment) != null) {
            registryEntryRepository
                    .findActiveResidentEmailsByUnit(tenantId, com.packid.api.domain.model.RegistryEntry.EntryType.RESIDENT, block.trim(), apartment.trim())
                    .stream()
                    .map(this::clean)
                    .filter(this::looksLikeEmail)
                    .forEach(email -> addCaseInsensitive(emails, email));
        }
        if (extras != null) {
            extras.stream()
                    .map(this::clean)
                    .filter(this::looksLikeEmail)
                    .forEach(email -> addCaseInsensitive(emails, email));
        }
        return List.copyOf(emails);
    }

    public void publish(
            UUID tenantId,
            String block,
            String apartment,
            Collection<String> extraRecipients,
            String changeType,
            String title,
            String details,
            String actor
    ) {
        String cleanedBlock = clean(block);
        String cleanedApartment = clean(apartment);
        if (tenantId == null || cleanedBlock == null || cleanedApartment == null) return;
        if (!emailNotificationsEnabled(tenantId)) return;

        List<String> recipients = residentEmails(tenantId, cleanedBlock, cleanedApartment, extraRecipients);
        if (recipients.isEmpty()) return;

        applicationEventPublisher.publishEvent(new UnitChangeEmailEvent(
                tenantId,
                cleanedBlock,
                cleanedApartment,
                recipients,
                clean(changeType) == null ? "UNIT_CHANGE" : changeType.trim(),
                clean(title) == null ? "Alteração na unidade" : title.trim(),
                clean(details) == null ? "Foi realizada uma alteração nos dados da unidade." : details.trim(),
                clean(actor) == null ? "sistema" : actor.trim(),
                LocalDateTime.now()
        ));
    }

    private boolean emailNotificationsEnabled(UUID tenantId) {
        return condominiumRepository.findAllByTenantIdAndDeletedFalse(tenantId).stream()
                .findFirst()
                .map(condominium -> !Boolean.FALSE.equals(condominium.getEmailNotificationsEnabled()))
                .orElse(true);
    }

    private void addCaseInsensitive(LinkedHashSet<String> emails, String email) {
        String key = email.toLowerCase(Locale.ROOT);
        boolean exists = emails.stream().anyMatch(item -> item.toLowerCase(Locale.ROOT).equals(key));
        if (!exists) emails.add(email);
    }

    private boolean looksLikeEmail(String email) {
        if (email == null) return false;
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 3 && email.indexOf('.', at) > at + 1;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
