package com.packid.api.service.notification;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class UnitChangeNotificationPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public UnitChangeNotificationPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
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

        applicationEventPublisher.publishEvent(new UnitChangeEmailEvent(
                tenantId,
                cleanedBlock,
                cleanedApartment,
                extraRecipients == null ? List.of() : List.copyOf(extraRecipients),
                clean(changeType) == null ? "UNIT_CHANGE" : changeType.trim(),
                clean(title) == null ? "Alteração na unidade" : title.trim(),
                clean(details) == null ? "Foi realizada uma alteração nos dados da unidade." : details.trim(),
                clean(actor) == null ? "sistema" : actor.trim(),
                LocalDateTime.now()
        ));
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
