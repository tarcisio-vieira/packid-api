package com.packid.api.controller.servicecompany;

import com.packid.api.controller.servicecompany.dto.ServiceCompanyRequest;
import com.packid.api.controller.servicecompany.dto.ServiceCompanyResponse;
import com.packid.api.service.ServiceCompanyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/service-companies")
public class ServiceCompanyController {
    private final ServiceCompanyService service;
    public ServiceCompanyController(ServiceCompanyService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<ServiceCompanyResponse>> getAll(@AuthenticationPrincipal OidcUser user) {
        return ResponseEntity.ok(service.getAll(user));
    }

    @PostMapping
    public ResponseEntity<ServiceCompanyResponse> create(@AuthenticationPrincipal OidcUser user, @Valid @RequestBody ServiceCompanyRequest request) {
        ServiceCompanyResponse created = service.create(user, request);
        return ResponseEntity.created(URI.create("/api/service-companies/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceCompanyResponse> update(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id,
                                                          @Valid @RequestBody ServiceCompanyRequest request) {
        return ResponseEntity.ok(service.update(user, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
