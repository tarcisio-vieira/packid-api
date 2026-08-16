package com.packid.api.controller.servicerecord;

import com.packid.api.controller.servicerecord.dto.ServiceRecordRequest;
import com.packid.api.controller.servicerecord.dto.ServiceRecordResponse;
import com.packid.api.domain.model.ServiceRecord.ServiceScope;
import com.packid.api.service.ServiceRecordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-records")
public class ServiceRecordController {
    private final ServiceRecordService service;
    public ServiceRecordController(ServiceRecordService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ServiceRecordResponse> create(@AuthenticationPrincipal OidcUser user, @Valid @RequestBody ServiceRecordRequest request) {
        ServiceRecordResponse created = service.create(user, request);
        return ResponseEntity.created(URI.create("/api/service-records/" + created.id())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ServiceRecordResponse>> get(@AuthenticationPrincipal OidcUser user,
                                                            @RequestParam(required = false) UUID providerId,
                                                            @RequestParam(required = false) ServiceScope scope) {
        return ResponseEntity.ok(service.get(user, providerId, scope));
    }
}
