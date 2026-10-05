package com.packid.api.controller.settings;

import com.packid.api.controller.settings.dto.BankIntegrationSettingsRequest;
import com.packid.api.controller.settings.dto.BankIntegrationSettingsResponse;
import com.packid.api.service.BankIntegrationSettingsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/bank-integration")
public class BankIntegrationSettingsController {
    private final BankIntegrationSettingsService service;

    public BankIntegrationSettingsController(BankIntegrationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public BankIntegrationSettingsResponse get(@AuthenticationPrincipal OidcUser user) {
        return service.get(user);
    }

    @PutMapping
    public BankIntegrationSettingsResponse update(
            @AuthenticationPrincipal OidcUser user,
            @RequestBody BankIntegrationSettingsRequest request
    ) {
        return service.update(user, request);
    }
}
