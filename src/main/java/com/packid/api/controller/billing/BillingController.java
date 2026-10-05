package com.packid.api.controller.billing;

import com.packid.api.controller.billing.dto.BillingChargeCreateRequest;
import com.packid.api.controller.billing.dto.BillingChargeResponse;
import com.packid.api.controller.billing.dto.BillingEmailRequest;
import com.packid.api.service.BillingService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/billing")
public class BillingController {
    private final BillingService service;
    public BillingController(BillingService service) { this.service = service; }

    @GetMapping("/charges")
    public List<BillingChargeResponse> list(@AuthenticationPrincipal OidcUser user) { return service.listForAdministration(user); }

    @PostMapping("/charges")
    public BillingChargeResponse create(@AuthenticationPrincipal OidcUser user, @Valid @RequestBody BillingChargeCreateRequest request) {
        return service.createManual(user, request);
    }

    @PostMapping("/charges/{id}/email")
    public void email(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id, @RequestBody(required = false) BillingEmailRequest request) {
        service.sendByEmail(user, id, request == null ? null : request.email());
    }

    @GetMapping(value = "/charges/{id}/print", produces = MediaType.TEXT_HTML_VALUE)
    public String print(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) { return service.printForAdministration(user, id); }

    @PostMapping("/sync")
    public void sync(@AuthenticationPrincipal OidcUser user) { service.requestBankSync(user); }
}
