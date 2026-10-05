package com.packid.api.service;

import com.packid.api.domain.model.ApartmentOccupancy;
import com.packid.api.domain.model.Condominium;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.CondominiumRepository;
import com.packid.api.integration.google.GoogleGmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ResidentCredentialEmailService {
    private static final Logger log = LoggerFactory.getLogger(ResidentCredentialEmailService.class);
    private static final String ACCESS_URL = "https://app.vsgi.com.br/condominio/user";

    private final CondominiumRepository condominiumRepository;
    private final GoogleGmailService gmailService;

    public ResidentCredentialEmailService(
            CondominiumRepository condominiumRepository,
            GoogleGmailService gmailService
    ) {
        this.condominiumRepository = condominiumRepository;
        this.gmailService = gmailService;
    }

    public void sendIfEnabled(
            UUID tenantId,
            RegistryEntry resident,
            ApartmentOccupancy occupancy,
            String plainPassword,
            boolean reset
    ) {
        if (resident == null || occupancy == null || plainPassword == null || plainPassword.isBlank()) return;
        if (!Boolean.TRUE.equals(resident.getResidentCredentialEmailEnabled())) return;
        String recipient = clean(resident.getEmail());
        if (recipient == null) return;

        List<Condominium> condominiums = condominiumRepository.findAllByTenantIdAndDeletedFalse(tenantId);
        Condominium condominium = condominiums.isEmpty() ? null : condominiums.get(0);
        if (condominium == null || !Boolean.TRUE.equals(condominium.getResidentCredentialEmailsEnabled())) return;

        String name = clean(condominium.getName()) == null ? "VSGI Condomínio" : condominium.getName().trim();
        String subject = reset ? "Nova senha de acesso ao " + name : "Acesso ao " + name;
        String plain = "Olá, " + resident.getName() + ",\n\n"
                + (reset ? "Foi gerada uma nova senha para o seu acesso.\n\n"
                         : "Seu acesso ao aplicativo do condomínio foi liberado.\n\n")
                + "Condomínio: " + name + "\n"
                + "Bloco: " + occupancy.getBlock() + "\n"
                + "Apartamento: " + occupancy.getApartment() + "\n"
                + "Usuário: " + resident.getResidentUsername() + "\n"
                + "Senha temporária: " + plainPassword + "\n"
                + "Acesso: " + ACCESS_URL + "\n\n"
                + "Por segurança, altere a senha no primeiro acesso.\n\nVSGI Condomínio";
        String html = "<p>Olá, " + esc(resident.getName()) + ",</p>"
                + "<p>" + (reset ? "Foi gerada uma <strong>nova senha</strong> para o seu acesso."
                                   : "Seu acesso ao aplicativo do condomínio foi liberado.") + "</p>"
                + "<p><strong>Condomínio:</strong> " + esc(name) + "<br>"
                + "<strong>Bloco:</strong> " + esc(occupancy.getBlock()) + "<br>"
                + "<strong>Apartamento:</strong> " + esc(occupancy.getApartment()) + "<br>"
                + "<strong>Usuário:</strong> " + esc(resident.getResidentUsername()) + "<br>"
                + "<strong>Senha temporária:</strong> " + esc(plainPassword) + "</p>"
                + "<p>Acesse <strong>" + ACCESS_URL + "</strong>.</p>"
                + "<p><strong>Por segurança, altere a senha no primeiro acesso.</strong></p>"
                + "<p>VSGI Condomínio</p>";

        try {
            gmailService.send(tenantId, recipient, subject, plain, html, name);
        } catch (Exception ex) {
            log.warn("Credenciais do morador {} ({}/{}) salvas, mas não foi possível enviar para {}: {}",
                    resident.getName(), occupancy.getBlock(), occupancy.getApartment(), recipient, ex.getMessage());
        }
    }

    private String clean(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isBlank() ? null : v;
    }

    private String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
