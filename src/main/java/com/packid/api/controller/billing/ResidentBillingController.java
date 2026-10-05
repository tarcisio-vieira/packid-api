package com.packid.api.controller.billing;

import com.packid.api.controller.billing.dto.BillingChargeResponse;
import com.packid.api.service.BillingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resident/billing")
public class ResidentBillingController {
    private final BillingService service;
    public ResidentBillingController(BillingService service) { this.service = service; }

    @GetMapping("/charges")
    public List<BillingChargeResponse> list(HttpSession session) { return service.listForResident(session); }

    @GetMapping(value = "/charges/{id}/print", produces = MediaType.TEXT_HTML_VALUE)
    public String print(HttpSession session, @PathVariable UUID id) { return service.printForResident(session, id); }
}
