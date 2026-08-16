package com.packid.api.service.notification;

import com.packid.api.domain.model.EmailNotificationLog;
import com.packid.api.domain.model.Tenant;
import com.packid.api.domain.model.TenantGoogleAccount;
import com.packid.api.domain.repository.EmailNotificationLogRepository;
import com.packid.api.domain.repository.TenantRepository;
import com.packid.api.integration.google.GoogleGmailService;
import com.packid.api.integration.google.TenantGoogleAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class UnitChangeEmailListener {
    private static final Logger log = LoggerFactory.getLogger(UnitChangeEmailListener.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final GoogleGmailService gmailService;
    private final TenantGoogleAccountService googleAccountService;
    private final TenantRepository tenantRepository;
    private final EmailNotificationLogRepository logRepository;

    public UnitChangeEmailListener(
            GoogleGmailService gmailService,
            TenantGoogleAccountService googleAccountService,
            TenantRepository tenantRepository,
            EmailNotificationLogRepository logRepository
    ) {
        this.gmailService = gmailService;
        this.googleAccountService = googleAccountService;
        this.tenantRepository = tenantRepository;
        this.logRepository = logRepository;
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitChange(UnitChangeEmailEvent event) {
        TenantGoogleAccount account = googleAccountService.find(event.tenantId()).orElse(null);
        if (account == null || account.getRefreshTokenEncrypted() == null
                || !Boolean.TRUE.equals(account.getGmailEnabled())) {
            log.debug("Gmail oficial não conectado para o tenant {}. Evento ignorado: {}",
                    event.tenantId(), event.changeType());
            return;
        }

        String sender = clean(account.getEmail());
        if (sender == null) return;

        String condominiumName = tenantRepository.findByIdAndDeletedFalse(event.tenantId())
                .map(Tenant::getName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("Condomínio");

        String subject = "VSGI Condomínio - " + event.title() + " - Bloco "
                + event.block() + " / Apto " + event.apartment();

        for (String recipient : event.recipients()) {
            sendOne(event, condominiumName, sender, recipient, subject);
        }
    }

    private void sendOne(UnitChangeEmailEvent event, String condominiumName,
                         String sender, String recipient, String subject) {
        EmailNotificationLog notificationLog = baseLog(event, sender, recipient, subject);
        try {
            gmailService.send(
                    event.tenantId(), recipient, subject,
                    plainBody(event, condominiumName),
                    htmlBody(event, condominiumName),
                    "VSGI Condomínio - " + condominiumName);
            notificationLog.setStatus("SENT");
            notificationLog.setSentAt(LocalDateTime.now());
            logRepository.save(notificationLog);
        } catch (Exception ex) {
            notificationLog.setStatus("FAILED");
            notificationLog.setErrorMessage(limit(ex.getMessage(), 4000));
            logRepository.save(notificationLog);
            log.warn("Falha ao enviar e-mail de alteração da unidade para {}: {}", recipient, ex.getMessage());
        }
    }

    private EmailNotificationLog baseLog(UnitChangeEmailEvent event, String sender,
                                         String recipient, String subject) {
        EmailNotificationLog item = new EmailNotificationLog();
        item.setTenantId(event.tenantId());
        item.setBlock(event.block());
        item.setApartment(event.apartment());
        item.setRecipientEmail(recipient);
        item.setSenderEmail(sender);
        item.setSubject(limit(subject, 255));
        item.setChangeType(limit(event.changeType(), 60));
        item.setStatus("PENDING");
        item.setCreatedBy(limit(event.actor(), 150));
        return item;
    }

    private String plainBody(UnitChangeEmailEvent event, String condominiumName) {
        if (isPackIdReceived(event)) {
            return "VSGI Condomínio\n\n"
                    + "Olá,\n\n"
                    + event.details() + "\n\n"
                    + "Condomínio: " + condominiumName + "\n"
                    + "Unidade: Bloco " + event.block() + " / Apartamento " + event.apartment() + "\n"
                    + "Alteração: " + event.title() + "\n"
                    + "Data/Hora: " + event.occurredAt().format(DATE_TIME) + "\n"
                    + "Realizado por: " + event.actor() + "\n\n"
                    + "Esta é uma mensagem automática do VSGI Condomínio. Não responda a este e-mail.";
        }

        return "Olá,\n\n"
                + "Foi realizada uma alteração nos dados da sua unidade no VSGI Condomínio.\n\n"
                + "Condomínio: " + condominiumName + "\n"
                + "Unidade: Bloco " + event.block() + " / Apartamento " + event.apartment() + "\n"
                + "Alteração: " + event.title() + "\n"
                + "Detalhes: " + event.details() + "\n"
                + "Data/Hora: " + event.occurredAt().format(DATE_TIME) + "\n"
                + "Realizado por: " + event.actor() + "\n\n"
                + "Esta é uma mensagem automática do VSGI Condomínio.\n"
                + "Não responda a este e-mail.";
    }

    private String htmlBody(UnitChangeEmailEvent event, String condominiumName) {
        if (isPackIdReceived(event)) {
            return "<div style=\"font-family:Arial,sans-serif;color:#222;line-height:1.5\">"
                    + "<h2 style=\"margin-bottom:8px\">VSGI Condomínio</h2>"
                    + "<p>Olá,</p>"
                    + "<p>" + HtmlUtils.htmlEscape(event.details()) + "</p>"
                    + "<table style=\"border-collapse:collapse\">"
                    + row("Condomínio", condominiumName)
                    + row("Unidade", "Bloco " + event.block() + " / Apartamento " + event.apartment())
                    + row("Alteração", event.title())
                    + row("Data/Hora", event.occurredAt().format(DATE_TIME))
                    + row("Realizado por", event.actor())
                    + "</table>"
                    + "<p style=\"margin-top:22px;color:#666;font-size:12px\">"
                    + "Esta é uma mensagem automática do VSGI Condomínio. Não responda a este e-mail."
                    + "</p></div>";
        }

        return "<div style=\"font-family:Arial,sans-serif;color:#222;line-height:1.5\">"
                + "<h2 style=\"margin-bottom:8px\">VSGI Condomínio</h2>"
                + "<p>Olá,</p><p>Foi realizada uma alteração nos dados da sua unidade.</p>"
                + "<table style=\"border-collapse:collapse\">"
                + row("Condomínio", condominiumName)
                + row("Unidade", "Bloco " + event.block() + " / Apartamento " + event.apartment())
                + row("Alteração", event.title())
                + row("Detalhes", event.details())
                + row("Data/Hora", event.occurredAt().format(DATE_TIME))
                + row("Realizado por", event.actor())
                + "</table>"
                + "<p style=\"margin-top:22px;color:#666;font-size:12px\">"
                + "Esta é uma mensagem automática do VSGI Condomínio. Não responda a este e-mail."
                + "</p></div>";
    }

    private boolean isPackIdReceived(UnitChangeEmailEvent event) {
        return "PACKID_RECEIVED".equalsIgnoreCase(event.changeType());
    }

    private String row(String label, String value) {
        return "<tr><td style=\"padding:5px 12px 5px 0;font-weight:bold;vertical-align:top\">"
                + HtmlUtils.htmlEscape(label)
                + "</td><td style=\"padding:5px 0\">"
                + HtmlUtils.htmlEscape(value == null ? "-" : value)
                + "</td></tr>";
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
