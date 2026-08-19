package com.packid.api.controller.settings;

import com.packid.api.controller.settings.dto.CondominiumSettingsResponse;

import com.packid.api.domain.model.Condominium;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.service.CondominiumBrandingService;
import com.packid.api.service.CondominiumSettingsService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/branding")
public class CondominiumBrandingController {
    private final CondominiumBrandingService brandingService;
    private final CondominiumSettingsService settingsService;

    public CondominiumBrandingController(CondominiumBrandingService brandingService,
                                         CondominiumSettingsService settingsService) {
        this.brandingService = brandingService;
        this.settingsService = settingsService;
    }

    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo(@AuthenticationPrincipal OidcUser user) {
        return photoResponse(brandingService.download(user));
    }

    @PutMapping(path = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CondominiumSettingsResponse upload(@AuthenticationPrincipal OidcUser user,
                                               @RequestPart("file") MultipartFile file) {
        Condominium saved = brandingService.uploadLogo(user, file);
        return settingsService.responseFor(settingsService.requireAdmin(user));
    }

    @DeleteMapping("/logo")
    public CondominiumSettingsResponse delete(@AuthenticationPrincipal OidcUser user) {
        brandingService.deleteLogo(user);
        return settingsService.responseFor(settingsService.requireAdmin(user));
    }

    public static ResponseEntity<byte[]> photoResponse(GoogleDrivePhotoService.PhotoContent photo) {
        MediaType type;
        try { type = MediaType.parseMediaType(photo.mimeType()); }
        catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.bytes());
    }
}
