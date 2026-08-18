package com.packid.api.controller.resident;

import com.packid.api.controller.resident.dto.ResidentPortalResponse;
import com.packid.api.controller.resident.dto.ResidentProfileUpdateRequest;
import com.packid.api.controller.registry.dto.RegistryEntryResponse;
import com.packid.api.controller.space.dto.SpaceAccessResponse;
import com.packid.api.controller.space.dto.SpaceKeyAvailabilityResponse;
import com.packid.api.domain.model.SpaceAccessRequest;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.service.ResidentPortalService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/resident")
public class ResidentPortalController {
    private final ResidentPortalService service;

    public ResidentPortalController(ResidentPortalService service) {
        this.service = service;
    }

    @GetMapping("/portal")
    public ResidentPortalResponse portal(HttpSession session) {
        return service.portal(session);
    }

    @GetMapping("/photos/{entryId}")
    public ResponseEntity<byte[]> photo(HttpSession session, @PathVariable UUID entryId) {
        GoogleDrivePhotoService.PhotoContent photo = service.photo(session, entryId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(photo.mimeType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.bytes());
    }

    @PutMapping("/profile/{entryId}")
    public RegistryEntryResponse updateProfile(
            HttpSession session, @PathVariable UUID entryId,
            @Valid @RequestBody ResidentProfileUpdateRequest request
    ) {
        return service.updateProfile(session, entryId, request);
    }

    @PutMapping(path = "/profile/{entryId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RegistryEntryResponse uploadProfilePhoto(
            HttpSession session, @PathVariable UUID entryId,
            @RequestPart("file") MultipartFile file
    ) {
        return service.uploadProfilePhoto(session, entryId, file);
    }

    @GetMapping("/spaces")
    public List<SpaceAccessResponse> spaces(HttpSession session) {
        return service.spaces(session);
    }

    @GetMapping("/spaces/{spaceType}/availability")
    public SpaceKeyAvailabilityResponse spaceAvailability(
            HttpSession session,
            @PathVariable SpaceAccessRequest.SpaceType spaceType
    ) {
        return service.spaceAvailability(session, spaceType);
    }

    @PostMapping("/spaces/{spaceType}/request")
    public SpaceAccessResponse toggleSpace(
            HttpSession session,
            @PathVariable SpaceAccessRequest.SpaceType spaceType,
            @RequestParam(required = false, defaultValue = "false") boolean assumeResponsibility
    ) {
        return service.toggleSpace(session, spaceType, assumeResponsibility);
    }
}
