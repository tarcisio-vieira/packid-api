package com.packid.api.controller.registry;

import com.packid.api.controller.registry.dto.VisitorVisitRequest;
import com.packid.api.controller.registry.dto.VisitorVisitResponse;
import com.packid.api.service.VisitorVisitService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/visits")
public class VisitorVisitController {

    private final VisitorVisitService service;

    public VisitorVisitController(VisitorVisitService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<VisitorVisitResponse> create(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody VisitorVisitRequest request
    ) {
        return ResponseEntity.ok(service.create(user, request));
    }

    @GetMapping
    public ResponseEntity<List<VisitorVisitResponse>> getByVisitor(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam UUID visitorId
    ) {
        return ResponseEntity.ok(service.getByVisitor(user, visitorId));
    }
}
