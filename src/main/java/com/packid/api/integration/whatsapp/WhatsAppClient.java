package com.packid.api.integration.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);

    private final RestClient restClient;
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;

    public WhatsAppClient(WhatsAppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://graph.facebook.com")
                .build();
    }

    public String sendArrivalTemplate(String toPhone) {
        validateConfiguration();

        log.info(
                "Enviando requisição para WhatsApp API. toPhone='{}', phoneNumberId='{}', template='{}', language='{}', apiVersion='{}'",
                toPhone,
                properties.getPhoneNumberId(),
                properties.getTemplateName(),
                properties.getLanguage(),
                properties.getApiVersion()
        );

        SendTemplateRequest request = new SendTemplateRequest(
                "whatsapp",
                toPhone,
                "template",
                new Template(
                        properties.getTemplateName(),
                        new Language(properties.getLanguage()),
                        null
                )
        );

        String responseBody = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(properties.getApiVersion(), properties.getPhoneNumberId(), "messages")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);

        log.info("Resposta bruta WhatsApp API para toPhone='{}': {}", toPhone, responseBody);

        try {
            SendTemplateResponse response = objectMapper.readValue(responseBody, SendTemplateResponse.class);

            if (response == null || response.messages() == null || response.messages().isEmpty()) {
                throw new IllegalStateException("Resposta vazia ao enviar mensagem WhatsApp. Body: " + responseBody);
            }

            String messageId = response.messages().get(0).id();

            log.info(
                    "Resposta interpretada com sucesso da WhatsApp API. toPhone='{}', messageId='{}'",
                    toPhone,
                    messageId
            );

            return messageId;
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Não foi possível interpretar a resposta do WhatsApp. Body: " + responseBody,
                    ex
            );
        }
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Integração WhatsApp está desabilitada");
        }
        if (!StringUtils.hasText(properties.getPhoneNumberId())) {
            throw new IllegalStateException("whatsapp.phoneNumberId não configurado");
        }
        if (!StringUtils.hasText(properties.getAccessToken())) {
            throw new IllegalStateException("whatsapp.accessToken não configurado");
        }
        if (!StringUtils.hasText(properties.getTemplateName())) {
            throw new IllegalStateException("whatsapp.templateName não configurado");
        }
        if (!StringUtils.hasText(properties.getLanguage())) {
            throw new IllegalStateException("whatsapp.language não configurado");
        }
    }

    private record SendTemplateRequest(
            String messaging_product,
            String to,
            String type,
            Template template
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Template(
            String name,
            Language language,
            List<Component> components
    ) {}

    private record Language(
            String code
    ) {}

    private record Component(
            String type,
            List<Parameter> parameters
    ) {}

    private record Parameter(
            String type,
            String text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SendTemplateResponse(
            List<MessageRef> messages
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessageRef(
            String id
    ) {}
}