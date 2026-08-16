package com.packid.api.controller.registry;

import com.packid.api.controller.registry.dto.RegistryEntryRequest;
import com.packid.api.controller.registry.dto.RegistryEntryResponse;
import com.packid.api.controller.registry.dto.UnitRegistrySummaryResponse;
import com.packid.api.domain.model.RegistryEntry.EntryType;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.service.RegistryEntryService;
import com.packid.api.service.RegistryPhotoService;
import com.packid.api.service.RegistryDocumentPhotoService;
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
    private final RegistryDocumentPhotoService documentPhotoService;

    public RegistryEntryController(RegistryEntryService service, RegistryPhotoService photoService,
                                   RegistryDocumentPhotoService documentPhotoService) {
        this.service = service;
        this.photoService = photoService;
        this.documentPhotoService = documentPhotoService;
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
            @RequestParam String apartment,
            @RequestParam(required = false) UUID occupancyId
    ) {
        return ResponseEntity.ok(service.getUnitSummary(user, block, apartment, occupancyId));
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


    @PutMapping(path = "/{id}/documents/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RegistryEntryResponse> uploadDocumentPhoto(
            @AuthenticationPrincipal OidcUser user,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @PathVariable UUID id,
            @PathVariable String kind,
            @RequestPart("file") MultipartFile file
    ) {
        documentPhotoService.upload(user, id, documentPhotoService.kind(kind), file, authorizedClient);
        return ResponseEntity.ok(service.getById(user, id));
    }

    @GetMapping("/{id}/documents/{kind}")
    public ResponseEntity<byte[]> getDocumentPhoto(
            @AuthenticationPrincipal OidcUser user,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @PathVariable UUID id,
            @PathVariable String kind
    ) {
        GoogleDrivePhotoService.PhotoContent photo = documentPhotoService.download(user, id, documentPhotoService.kind(kind), authorizedClient);
        MediaType contentType;
        try {
            contentType = photo.mimeType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(photo.mimeType());
        } catch (IllegalArgumentException ex) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(contentType).body(photo.bytes());
    }

    @DeleteMapping("/{id}/documents/{kind}")
    public ResponseEntity<RegistryEntryResponse> deleteDocumentPhoto(
            @AuthenticationPrincipal OidcUser user,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @PathVariable UUID id,
            @PathVariable String kind
    ) {
        documentPhotoService.delete(user, id, documentPhotoService.kind(kind), authorizedClient);
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
