package com.packid.api.controller.amenity;

import com.packid.api.controller.amenity.dto.*;
import com.packid.api.service.AmenityReservationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/amenity-spaces")
public class AmenitySpaceController {
    private final AmenityReservationService service;
    public AmenitySpaceController(AmenityReservationService service) { this.service = service; }

    @GetMapping
    public List<AmenitySpaceResponse> list(@AuthenticationPrincipal OidcUser user) { return service.staffSpaces(user); }

    @PostMapping
    public AmenitySpaceResponse create(@AuthenticationPrincipal OidcUser user, @Valid @RequestBody AmenitySpaceRequest request) {
        return service.createSpace(user, request);
    }

    @PutMapping("/{id}")
    public AmenitySpaceResponse update(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id, @Valid @RequestBody AmenitySpaceRequest request) {
        return service.updateSpace(user, id, request);
    }

    @PutMapping(path="/{id}/photo", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AmenitySpaceResponse photo(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return service.uploadPhoto(user, id, file);
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        var photo = service.photoStaff(user, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.mimeType()))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff").body(photo.bytes());
    }
}
