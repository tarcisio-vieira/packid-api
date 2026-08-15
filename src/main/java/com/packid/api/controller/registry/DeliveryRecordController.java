package com.packid.api.controller.registry;

import com.packid.api.controller.registry.dto.DeliveryRecordRequest;
import com.packid.api.controller.registry.dto.DeliveryRecordResponse;
import com.packid.api.service.DeliveryRecordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryRecordController {

    private final DeliveryRecordService service;

    public DeliveryRecordController(DeliveryRecordService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DeliveryRecordResponse> create(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody DeliveryRecordRequest request
    ) {
        return ResponseEntity.ok(service.create(user, request));
    }

    @GetMapping
    public ResponseEntity<List<DeliveryRecordResponse>> getByDeliveryPerson(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam UUID deliveryPersonId
    ) {
        return ResponseEntity.ok(service.getByDeliveryPerson(user, deliveryPersonId));
    }
}
