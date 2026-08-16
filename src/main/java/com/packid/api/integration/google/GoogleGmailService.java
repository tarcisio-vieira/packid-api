package com.packid.api.integration.google;

import com.packid.api.domain.model.TenantGoogleAccount;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@Service
public class GoogleGmailService {
    private final TenantGoogleAccountService googleAccountService;
    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://gmail.googleapis.com")
            .build();

    public GoogleGmailService(TenantGoogleAccountService googleAccountService) {
        this.googleAccountService = googleAccountService;
    }

    public void send(UUID tenantId, String recipient, String subject,
                     String plainBody, String htmlBody, String fromName) {
        TenantGoogleAccount account = googleAccountService.requireConnected(tenantId);
        if (!Boolean.TRUE.equals(account.getGmailEnabled())) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "O Gmail está desabilitado nas Configurações do condomínio.");
        }

        String accessToken = googleAccountService.freshAccessToken(tenantId);
        String raw = buildRawMessage(account.getEmail(), fromName, recipient, subject, plainBody, htmlBody);

        try {
            restClient.post()
                    .uri("/gmail/v1/users/me/messages/send")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("raw", raw))
                    .retrieve()
                    .toBodilessEntity();
            googleAccountService.recordGmailSuccess(tenantId);
        } catch (RestClientResponseException ex) {
            String googleBody = ex.getResponseBodyAsString();
            String normalized = googleBody == null ? "" : googleBody.toLowerCase();

            if (normalized.contains("service_disabled")
                    || normalized.contains("accessnotconfigured")
                    || (normalized.contains("gmail.googleapis.com") && normalized.contains("disabled"))) {
                String message = "A Gmail API não está habilitada no projeto Google Cloud usado pelo VSGI. Habilite a Gmail API no mesmo projeto do GOOGLE_CLIENT_ID e depois use 'Testar Gmail' em Configurações.";
                googleAccountService.recordGoogleError(tenantId, message, false);
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, message, ex);
            }

            if (normalized.contains("access_token_scope_insufficient")
                    || normalized.contains("insufficient authentication scopes")
                    || normalized.contains("insufficientpermissions")) {
                String message = "A conta Google oficial não concedeu a permissão gmail.send. Reconecte a conta em Configurações e aceite a permissão para o VSGI enviar e-mails.";
                googleAccountService.recordGoogleError(tenantId, message, true);
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, message, ex);
            }

            if (ex.getStatusCode().value() == 401) {
                String message = "A autorização da conta Google oficial expirou ou foi revogada. Reconecte a conta Google em Configurações.";
                googleAccountService.recordGoogleError(tenantId, message, true);
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, message, ex);
            }

            if (ex.getStatusCode().value() == 403) {
                String message = "O Google recusou o envio pelo Gmail. Verifique se a Gmail API está habilitada e se a conta oficial concedeu a permissão de envio.";
                googleAccountService.recordGoogleError(tenantId, message, false);
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, message, ex);
            }

            String message = "Não foi possível enviar a notificação pelo Gmail (HTTP " + ex.getStatusCode().value() + ").";
            googleAccountService.recordGoogleError(tenantId, message, false);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, ex);
        }
    }

    private String buildRawMessage(String from, String fromName, String to,
                                   String subject, String plainBody, String htmlBody) {
        try {
            Session session = Session.getInstance(new Properties());
            MimeMessage message = new MimeMessage(session);
            String displayName = clean(fromName) == null ? "VSGI Condomínio" : fromName;
            message.setFrom(new InternetAddress(from, displayName, StandardCharsets.UTF_8.name()));
            message.setRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject, StandardCharsets.UTF_8.name());

            MimeBodyPart plainPart = new MimeBodyPart();
            plainPart.setText(plainBody, StandardCharsets.UTF_8.name());
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

            MimeMultipart alternative = new MimeMultipart("alternative");
            alternative.addBodyPart(plainPart);
            alternative.addBodyPart(htmlPart);
            message.setContent(alternative);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            message.writeTo(out);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível preparar a notificação de e-mail.", ex);
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
