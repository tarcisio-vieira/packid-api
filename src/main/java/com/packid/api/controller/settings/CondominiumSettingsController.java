package com.packid.api.controller.settings;

import com.packid.api.controller.settings.dto.CondominiumSettingsResponse;
import com.packid.api.controller.settings.dto.CondominiumSettingsUpdateRequest;
import com.packid.api.controller.settings.dto.PackIdLabelPrintSettingsResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.integration.google.GoogleGmailService;
import com.packid.api.integration.google.TenantGoogleAccountService;
import com.packid.api.service.CondominiumSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/settings")
public class CondominiumSettingsController {
    public static final String FORCE_GOOGLE_CONSENT = "VSGI_FORCE_GOOGLE_CONSENT";
    public static final String RETURN_TO_SETTINGS = "VSGI_RETURN_TO_SETTINGS";
    public static final String PREVIOUS_AUTHENTICATION = "VSGI_PREVIOUS_AUTHENTICATION";
    public static final String PENDING_TENANT_ID = "VSGI_PENDING_TENANT_ID";
    public static final String PENDING_ACTOR = "VSGI_PENDING_ACTOR";

    private final CondominiumSettingsService settingsService;
    private final TenantGoogleAccountService googleAccountService;
    private final GoogleGmailService gmailService;

    public CondominiumSettingsController(
            CondominiumSettingsService settingsService,
            TenantGoogleAccountService googleAccountService,
            GoogleGmailService gmailService
    ) {
        this.settingsService = settingsService;
        this.googleAccountService = googleAccountService;
        this.gmailService = gmailService;
    }

    @GetMapping("/condominium")
    public CondominiumSettingsResponse get(@AuthenticationPrincipal OidcUser user) {
        return settingsService.get(user);
    }

    @PutMapping("/condominium")
    public CondominiumSettingsResponse update(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody CondominiumSettingsUpdateRequest request
    ) {
        return settingsService.update(user, request);
    }

    @GetMapping("/label-print")
    public PackIdLabelPrintSettingsResponse labelPrintSettings(@AuthenticationPrincipal OidcUser user) {
        return settingsService.labelPrintSettings(user);
    }

    @GetMapping("/google-account/authorize")
    public ResponseEntity<Void> authorizeGoogle(
            @AuthenticationPrincipal OidcUser user,
            HttpServletRequest request
    ) {
        AppUser appUser = settingsService.requireAdmin(user);
        Authentication currentAuthentication = SecurityContextHolder.getContext().getAuthentication();

        request.getSession(true).setAttribute(PREVIOUS_AUTHENTICATION, currentAuthentication);
        request.getSession(true).setAttribute(PENDING_TENANT_ID, appUser.getTenantId().toString());
        request.getSession(true).setAttribute(PENDING_ACTOR, appUser.getEmail());
        request.getSession(true).setAttribute(FORCE_GOOGLE_CONSENT, Boolean.TRUE);
        request.getSession(true).setAttribute(RETURN_TO_SETTINGS, Boolean.TRUE);

        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        return ResponseEntity.status(302)
                .location(URI.create(contextPath + "/oauth2/authorization/google"))
                .build();
    }

    @PostMapping("/google-account/test-gmail")
    public CondominiumSettingsResponse testGmail(@AuthenticationPrincipal OidcUser user) {
        AppUser appUser = settingsService.requireAdmin(user);
        var account = googleAccountService.requireConnected(appUser.getTenantId());
        String recipient = account.getEmail();
        gmailService.send(
                appUser.getTenantId(),
                recipient,
                "VSGI Condomínio - Teste de envio do Gmail",
                "Este é um e-mail de teste enviado pelo VSGI Condomínio. Se você recebeu esta mensagem, a integração com o Gmail está funcionando corretamente.",
                "<p>Este é um e-mail de teste enviado pelo <strong>VSGI Condomínio</strong>.</p>"
                        + "<p>Se você recebeu esta mensagem, a integração com o Gmail está funcionando corretamente.</p>",
                "VSGI Condomínio"
        );
        return settingsService.responseFor(appUser);
    }

    @DeleteMapping("/google-account")
    public CondominiumSettingsResponse disconnectGoogle(@AuthenticationPrincipal OidcUser user) {
        AppUser appUser = settingsService.requireAdmin(user);
        googleAccountService.disconnect(appUser);
        return settingsService.responseFor(appUser);
    }
}
