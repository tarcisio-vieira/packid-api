package com.packid.api.controller.export;

import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.service.ManagementExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/exports")
public class ManagementExportController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ManagementExportService service;

    public ManagementExportController(ManagementExportService service) {
        this.service = service;
    }

    @GetMapping("/registry")
    public ResponseEntity<byte[]> registry(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam RegistryEntry.EntryType type
    ) {
        return response(service.exportRegistry(user, type));
    }

    @GetMapping("/service-companies")
    public ResponseEntity<byte[]> serviceCompanies(@AuthenticationPrincipal OidcUser user) {
        return response(service.exportServiceCompanies(user));
    }

    @GetMapping("/space-access")
    public ResponseEntity<byte[]> spaceAccess(@AuthenticationPrincipal OidcUser user) {
        return response(service.exportSpaceAccess(user));
    }

    @GetMapping("/pool-cards")
    public ResponseEntity<byte[]> poolCards(@AuthenticationPrincipal OidcUser user) {
        return response(service.exportPoolCards(user));
    }

    private ResponseEntity<byte[]> response(ManagementExportService.ExportFile file) {
        String safeName = file.fileName().replace("\"", "");
        String encoded = java.net.URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeName + "\"; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }
}
