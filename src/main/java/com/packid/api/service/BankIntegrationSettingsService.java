package com.packid.api.service;

import com.packid.api.controller.settings.dto.BankIntegrationSettingsRequest;
import com.packid.api.controller.settings.dto.BankIntegrationSettingsResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.BankIntegrationConfig;
import com.packid.api.domain.repository.BankIntegrationConfigRepository;
import com.packid.api.integration.google.GoogleTokenCipher;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class BankIntegrationSettingsService {
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final BankIntegrationConfigRepository repository;
    private final GoogleTokenCipher cipher;

    public BankIntegrationSettingsService(
            AuthenticatedUserService authenticatedUserService,
            AccessControlService accessControlService,
            BankIntegrationConfigRepository repository,
            GoogleTokenCipher cipher
    ) {
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.repository = repository;
        this.cipher = cipher;
    }

    public BankIntegrationSettingsResponse get(OidcUser oidcUser) {
        AppUser user = requireManager(oidcUser);
        return repository.findByTenantIdAndDeletedFalse(user.getTenantId())
                .map(this::toResponse)
                .orElse(new BankIntegrationSettingsResponse(
                        "NONE", "SANDBOX", false, null, false,
                        null, null, null, null, null, null, null, null
                ));
    }

    @Transactional
    public BankIntegrationSettingsResponse update(OidcUser oidcUser, BankIntegrationSettingsRequest request) {
        AppUser user = requireManager(oidcUser);
        BankIntegrationConfig config = repository.findByTenantIdAndDeletedFalse(user.getTenantId())
                .orElseGet(() -> {
                    BankIntegrationConfig created = new BankIntegrationConfig();
                    created.setTenantId(user.getTenantId());
                    created.setCreatedBy(actor(user));
                    return created;
                });

        config.setProvider(normalizeProvider(request.provider()));
        config.setEnvironment(normalizeEnvironment(request.environment()));
        config.setEnabled(Boolean.TRUE.equals(request.enabled()));
        config.setClientId(clean(request.clientId()));
        if (request.clientSecret() != null && !request.clientSecret().isBlank()) {
            config.setClientSecretEncrypted(cipher.encrypt(request.clientSecret().trim()));
        }
        config.setApiBaseUrl(clean(request.apiBaseUrl()));
        config.setTokenUrl(clean(request.tokenUrl()));
        config.setWorkspaceId(clean(request.workspaceId()));
        config.setCovenantCode(clean(request.covenantCode()));
        config.setBeneficiaryCode(clean(request.beneficiaryCode()));
        config.setNotes(clean(request.notes()));
        config.setUpdatedBy(actor(user));
        repository.save(config);
        return toResponse(config);
    }

    public BankIntegrationConfig requireConfigured(AppUser user) {
        BankIntegrationConfig config = repository.findByTenantIdAndDeletedFalse(user.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                        "Configure a integração bancária antes de sincronizar boletos."));
        if (!Boolean.TRUE.equals(config.getEnabled()) || "NONE".equalsIgnoreCase(config.getProvider())) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "A integração bancária está desativada ou sem banco selecionado.");
        }
        return config;
    }

    private AppUser requireManager(OidcUser oidcUser) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);
        return user;
    }

    private BankIntegrationSettingsResponse toResponse(BankIntegrationConfig c) {
        return new BankIntegrationSettingsResponse(
                c.getProvider(), c.getEnvironment(), Boolean.TRUE.equals(c.getEnabled()), c.getClientId(),
                c.getClientSecretEncrypted() != null && !c.getClientSecretEncrypted().isBlank(),
                c.getApiBaseUrl(), c.getTokenUrl(), c.getWorkspaceId(), c.getCovenantCode(),
                c.getBeneficiaryCode(), c.getNotes(), c.getLastSyncAt(), c.getLastError()
        );
    }

    private String normalizeProvider(String value) {
        String v = clean(value);
        if (v == null) return "NONE";
        v = v.toUpperCase(Locale.ROOT);
        return switch (v) {
            case "NONE", "SANTANDER", "BANCO_DO_BRASIL", "ITAU", "BRADESCO", "OUTRO" -> v;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Banco/provedor inválido.");
        };
    }

    private String normalizeEnvironment(String value) {
        String v = clean(value);
        if (v == null) return "SANDBOX";
        v = v.toUpperCase(Locale.ROOT);
        if (!v.equals("SANDBOX") && !v.equals("PRODUCTION")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambiente bancário inválido.");
        }
        return v;
    }

    private String actor(AppUser user) { return clean(user.getEmail()) == null ? "system" : user.getEmail().trim(); }
    private String clean(String value) { return value == null || value.trim().isBlank() ? null : value.trim(); }
}
