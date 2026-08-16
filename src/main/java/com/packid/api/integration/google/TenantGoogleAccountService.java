package com.packid.api.integration.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.TenantGoogleAccount;
import com.packid.api.domain.repository.TenantGoogleAccountRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantGoogleAccountService {
    public static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    public static final String DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    private final TenantGoogleAccountRepository repository;
    private final GoogleTokenCipher tokenCipher;
    private final RestClient restClient = RestClient.create();
    private final String clientId;
    private final String clientSecret;

    public TenantGoogleAccountService(
            TenantGoogleAccountRepository repository,
            GoogleTokenCipher tokenCipher,
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret
    ) {
        this.repository = repository;
        this.tokenCipher = tokenCipher;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Transactional
    public TenantGoogleAccount connect(
            AppUser appUser,
            String officialEmail,
            String officialSubject,
            OAuth2AuthorizedClient authorizedClient
    ) {
        return connectForTenant(
                appUser.getTenantId(),
                actor(appUser),
                officialEmail,
                officialSubject,
                authorizedClient
        );
    }

    @Transactional
    public TenantGoogleAccount connectForTenant(
            UUID tenantId,
            String actor,
            String officialEmail,
            String officialSubject,
            OAuth2AuthorizedClient authorizedClient
    ) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant não identificado para a conta Google.");
        }
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A autorização Google da sessão não está disponível. Reconecte a conta Google.");
        }

        String email = clean(officialEmail);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A conta Google autenticada não informou um e-mail válido.");
        }

        Set<String> grantedScopes = authorizedClient.getAccessToken().getScopes();
        if (grantedScopes == null || !grantedScopes.contains(DRIVE_FILE_SCOPE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A permissão do Google Drive não foi concedida. Reconecte a conta e autorize o acesso aos arquivos criados pelo VSGI.");
        }
        if (!grantedScopes.contains(GMAIL_SEND_SCOPE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A permissão para enviar e-mails pelo Gmail não foi concedida. Reconecte a conta Google e autorize o VSGI a enviar e-mails.");
        }

        TenantGoogleAccount account = repository.findByTenantIdAndDeletedFalse(tenantId)
                .orElseGet(TenantGoogleAccount::new);

        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();
        if (refreshToken != null && clean(refreshToken.getTokenValue()) != null) {
            account.setRefreshTokenEncrypted(tokenCipher.encrypt(refreshToken.getTokenValue()));
        } else if (clean(account.getRefreshTokenEncrypted()) == null
                || !email.equalsIgnoreCase(clean(account.getEmail()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "O Google não retornou uma autorização permanente. Reconecte a conta Google e confirme as permissões do Gmail e do Drive.");
        }

        account.setTenantId(tenantId);
        account.setEmail(email);
        account.setProviderSubject(clean(officialSubject));
        account.setDriveEnabled(true);
        account.setGmailEnabled(true);
        account.setConnectedAt(LocalDateTime.now());
        account.setLastError(null);
        String normalizedActor = clean(actor) == null ? "system" : actor.trim();
        if (account.getId() == null) account.setCreatedBy(normalizedActor);
        else account.setUpdatedBy(normalizedActor);
        return repository.save(account);
    }

    public Optional<TenantGoogleAccount> find(UUID tenantId) {
        return repository.findByTenantIdAndDeletedFalse(tenantId);
    }

    public TenantGoogleAccount requireConnected(UUID tenantId) {
        TenantGoogleAccount account = repository.findByTenantIdAndDeletedFalse(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                        "Conecte a conta Google oficial do condomínio em Configurações."));
        if (clean(account.getRefreshTokenEncrypted()) == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "A conta Google do condomínio precisa ser reconectada em Configurações.");
        }
        return account;
    }

    public String getOfficialEmail(UUID tenantId) {
        return find(tenantId)
                .filter(account -> Boolean.TRUE.equals(account.getDriveEnabled()))
                .filter(account -> clean(account.getRefreshTokenEncrypted()) != null)
                .map(TenantGoogleAccount::getEmail)
                .map(this::clean)
                .orElse(null);
    }

    @Transactional
    public void disconnect(AppUser appUser) {
        repository.findByTenantIdAndDeletedFalse(appUser.getTenantId()).ifPresent(account -> {
            account.setRefreshTokenEncrypted(null);
            account.setDriveEnabled(false);
            account.setGmailEnabled(false);
            account.setConnectedAt(null);
            account.setLastRefreshAt(null);
            account.setLastError(null);
            account.setUpdatedBy(actor(appUser));
            repository.save(account);
        });
    }

    @Transactional
    public void recordGoogleError(UUID tenantId, String message, boolean disableGmail) {
        repository.findByTenantIdAndDeletedFalse(tenantId).ifPresent(account -> {
            account.setLastError(limit(message, 1000));
            if (disableGmail) account.setGmailEnabled(false);
            repository.save(account);
        });
    }

    @Transactional
    public void recordGmailSuccess(UUID tenantId) {
        repository.findByTenantIdAndDeletedFalse(tenantId).ifPresent(account -> {
            boolean changed = clean(account.getLastError()) != null || !Boolean.TRUE.equals(account.getGmailEnabled());
            if (!changed) return;
            account.setGmailEnabled(true);
            account.setLastError(null);
            repository.save(account);
        });
    }

    @Transactional
    public String freshAccessToken(UUID tenantId) {
        TenantGoogleAccount account = requireConnected(tenantId);
        final String refreshToken;
        try {
            refreshToken = tokenCipher.decrypt(account.getRefreshTokenEncrypted());
        } catch (RuntimeException ex) {
            account.setLastError(limit(ex.getMessage(), 1000));
            repository.save(account);
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "A autorização Google salva não pôde ser utilizada. Reconecte a conta Google em Configurações.", ex);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");

        try {
            GoogleTokenResponse response = restClient.post()
                    .uri(GOOGLE_TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);

            if (response == null || clean(response.accessToken()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "O Google não retornou um token de acesso válido.");
            }

            account.setLastRefreshAt(LocalDateTime.now());
            account.setLastError(null);
            repository.save(account);
            return response.accessToken();
        } catch (RestClientResponseException ex) {
            String detail = "Falha ao renovar autorização Google (HTTP " + ex.getStatusCode().value() + ").";
            account.setLastError(limit(detail + " " + ex.getResponseBodyAsString(), 1000));
            repository.save(account);
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "A conta Google do condomínio perdeu a autorização. Reconecte-a em Configurações.", ex);
        }
    }

    private String actor(AppUser appUser) {
        String email = clean(appUser.getEmail());
        return email == null ? "system" : email;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType,
            String scope
    ) {}
}
