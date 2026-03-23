package com.packid.api.integration.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class WhatsAppClient {

    private final RestClient restClient;
    private final WhatsAppProperties properties;

    public WhatsAppClient(WhatsAppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl("https://graph.facebook.com")
                .build();
    }

    public String sendArrivalTemplate(String toPhone,
                                      String residentName,
                                      String apartment,
                                      String packageCode) {
        validateConfiguration();

        SendTemplateRequest request = new SendTemplateRequest(
                "whatsapp",
                toPhone,
                "template",
                new Template(
                        properties.getTemplateName(),
                        new Language(properties.getLanguage()),
                        List.of(
                                new Component(
                                        "body",
                                        List.of(
                                                new Parameter("text", safe(residentName)),
                                                new Parameter("text", safe(apartment)),
                                                new Parameter("text", safe(packageCode))
                                        )
                                )
                        )
                )
        );

        SendTemplateResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .pathSegment(properties.getApiVersion(), properties.getPhoneNumberId(), "messages")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(SendTemplateResponse.class);

        if (response == null || response.messages() == null || response.messages().isEmpty()) {
            throw new IllegalStateException("Resposta vazia ao enviar mensagem WhatsApp");
        }

        return response.messages().get(0).id();
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

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private record SendTemplateRequest(
            String messaging_product,
            String to,
            String type,
            Template template
    ) {}

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