package com.packid.api.controller.settings;

import com.packid.api.controller.settings.dto.CondominiumSettingsResponse;
import com.packid.api.controller.settings.dto.CondominiumSettingsUpdateRequest;
import com.packid.api.domain.model.AppUser;
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

    public CondominiumSettingsController(
            CondominiumSettingsService settingsService,
            TenantGoogleAccountService googleAccountService
    ) {
        this.settingsService = settingsService;
        this.googleAccountService = googleAccountService;
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

    @DeleteMapping("/google-account")
    public CondominiumSettingsResponse disconnectGoogle(@AuthenticationPrincipal OidcUser user) {
        AppUser appUser = settingsService.requireAdmin(user);
        googleAccountService.disconnect(appUser);
        return settingsService.responseFor(appUser);
    }
}
