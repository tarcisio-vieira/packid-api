package com.packid.api.service;

import com.packid.api.domain.model.ApartmentOccupancy;
import com.packid.api.domain.model.Condominium;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.CondominiumRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.integration.google.GoogleGmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ResidentCredentialEmailService {
    private static final Logger log = LoggerFactory.getLogger(ResidentCredentialEmailService.class);
    private static final String ACCESS_URL = "https://app.vsgi.com.br/packid/user";

    private final CondominiumRepository condominiumRepository;
    private final RegistryEntryRepository registryEntryRepository;
    private final GoogleGmailService gmailService;

    public ResidentCredentialEmailService(
            CondominiumRepository condominiumRepository,
            RegistryEntryRepository registryEntryRepository,
            GoogleGmailService gmailService
    ) {
        this.condominiumRepository = condominiumRepository;
        this.registryEntryRepository = registryEntryRepository;
        this.gmailService = gmailService;
    }

    public void sendIfEnabled(UUID tenantId, ApartmentOccupancy occupancy, String plainPassword, boolean reset) {
        if (occupancy == null || plainPassword == null || plainPassword.isBlank()) return;
        if (!Boolean.TRUE.equals(occupancy.getCredentialEmailEnabled())) return;

        List<Condominium> condominiums = condominiumRepository.findAllByTenantIdAndDeletedFalse(tenantId);
        Condominium condominium = condominiums.isEmpty() ? null : condominiums.get(0);
        if (condominium == null || !Boolean.TRUE.equals(condominium.getResidentCredentialEmailsEnabled())) return;

        Set<String> recipients = new LinkedHashSet<>();
        registryEntryRepository
                .findAllByTenantIdAndOccupancyIdAndEntryTypeAndActiveTrueAndDeletedFalseOrderByNameAsc(
                        tenantId, occupancy.getId(), RegistryEntry.EntryType.RESIDENT)
                .stream()
                .map(RegistryEntry::getEmail)
                .filter(email -> clean(email) != null)
                .map(String::trim)
                .forEach(recipients::add);
        if (recipients.isEmpty()) return;

        String name = clean(condominium.getName()) == null ? "VSGI Condomínio" : condominium.getName().trim();
        String subject = reset ? "Nova senha de acesso ao " + name : "Acesso ao " + name;
        String plain = "Olá,\n\n"
                + (reset ? "Foi gerada uma nova senha para o acesso da sua unidade.\n\n"
                         : "O acesso de visualização da sua unidade foi liberado.\n\n")
                + "Condomínio: " + name + "\n"
                + "Bloco: " + occupancy.getBlock() + "\n"
                + "Apartamento: " + occupancy.getApartment() + "\n"
                + "Usuário: " + occupancy.getResidentUsername() + "\n"
                + "Senha temporária: " + plainPassword + "\n"
                + "Acesso: " + ACCESS_URL + "\n\n"
                + "Por segurança, altere a senha no primeiro acesso.\n\nVSGI Condomínio";
        String html = "<p>Olá,</p>"
                + "<p>" + (reset ? "Foi gerada uma <strong>nova senha</strong> para o acesso da sua unidade."
                                   : "O acesso de visualização da sua unidade foi liberado.") + "</p>"
                + "<p><strong>Condomínio:</strong> " + esc(name) + "<br>"
                + "<strong>Bloco:</strong> " + esc(occupancy.getBlock()) + "<br>"
                + "<strong>Apartamento:</strong> " + esc(occupancy.getApartment()) + "<br>"
                + "<strong>Usuário:</strong> " + esc(occupancy.getResidentUsername()) + "<br>"
                + "<strong>Senha temporária:</strong> " + esc(plainPassword) + "</p>"
                + "<p>Acesse <strong>" + ACCESS_URL + "</strong>.</p>"
                + "<p><strong>Por segurança, altere a senha no primeiro acesso.</strong></p>"
                + "<p>VSGI Condomínio</p>";

        for (String recipient : recipients) {
            try {
                gmailService.send(tenantId, recipient.trim(), subject, plain, html, name);
            } catch (Exception ex) {
                // A falha de e-mail não deve desfazer o cadastro ou a nova senha.
                log.warn("Credenciais da unidade {}/{} salvas, mas não foi possível enviar para {}: {}",
                        occupancy.getBlock(), occupancy.getApartment(), recipient, ex.getMessage());
            }
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
