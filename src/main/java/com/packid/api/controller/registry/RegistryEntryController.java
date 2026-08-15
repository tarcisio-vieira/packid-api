package com.packid.api.controller.registry;

import com.packid.api.controller.registry.dto.RegistryEntryRequest;
import com.packid.api.controller.registry.dto.RegistryEntryResponse;
import com.packid.api.controller.registry.dto.UnitRegistrySummaryResponse;
import com.packid.api.domain.model.RegistryEntry.EntryType;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.service.RegistryEntryService;
import com.packid.api.service.RegistryPhotoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/registry")
public class RegistryEntryController {

    private final RegistryEntryService service;
    private final RegistryPhotoService photoService;

    public RegistryEntryController(RegistryEntryService service, RegistryPhotoService photoService) {
        this.service = service;
        this.photoService = photoService;
    }

    @GetMapping
    public ResponseEntity<List<RegistryEntryResponse>> getAll(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam(required = false) EntryType type
    ) {
        return ResponseEntity.ok(service.getAll(user, type));
    }


    @GetMapping("/unit-summary")
    public ResponseEntity<UnitRegistrySummaryResponse> getUnitSummary(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam String block,
            @RequestParam String apartment
    ) {
        return ResponseEntity.ok(service.getUnitSummary(user, block, apartment));
    }

    @PostMapping
    public ResponseEntity<RegistryEntryResponse> create(
            @AuthenticationPrincipal OidcUser user,
            @Valid @RequestBody RegistryEntryRequest request
    ) {
        RegistryEntryResponse created = service.create(user, request);
        return ResponseEntity.created(URI.create("/api/registry/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RegistryEntryResponse> update(
            @AuthenticationPrincipal OidcUser user,
            @PathVariable UUID id,
            @Valid @RequestBody RegistryEntryRequest request
    ) {
        return ResponseEntity.ok(service.update(user, id, request));
    }

    @PutMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegistryEntryResponse> uploadPhoto(
            @AuthenticationPrincipal OidcUser user,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file
    ) {
        photoService.upload(user, id, file, authorizedClient);
        return ResponseEntity.ok(service.getById(user, id));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> getPhoto(
            @AuthenticationPrincipal OidcUser user,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @PathVariable UUID id
    ) {
        GoogleDrivePhotoService.PhotoContent photo = photoService.download(user, id, authorizedClient);
        MediaType contentType;
        try {
            contentType = photo.mimeType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(photo.mimeType());
        } catch (IllegalArgumentException ex) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(contentType)
                .body(photo.bytes());
    }

    @DeleteMapping("/{id}/photo")
    public ResponseEntity<RegistryEntryResponse> deletePhoto(
            @AuthenticationPrincipal OidcUser user,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @PathVariable UUID id
    ) {
        photoService.delete(user, id, authorizedClient);
        return ResponseEntity.ok(service.getById(user, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal OidcUser user,
            @PathVariable UUID id
    ) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
