package com.packid.api.controller.pool;

import com.packid.api.controller.pool.dto.PoolCardPageResponse;
import com.packid.api.controller.pool.dto.PoolCardRequest;
import com.packid.api.controller.pool.dto.PoolCardResidentOptionResponse;
import com.packid.api.controller.pool.dto.PoolCardResponse;
import com.packid.api.controller.pool.dto.PoolCardSettingsResponse;
import com.packid.api.domain.model.PoolCard;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.service.PoolCardDocumentService;
import com.packid.api.service.PoolCardPdfService;
import com.packid.api.service.PoolCardService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/pool-cards")
public class PoolCardController {
    private final PoolCardService service;
    private final PoolCardDocumentService documentService;
    private final PoolCardPdfService pdfService;

    public PoolCardController(PoolCardService service, PoolCardDocumentService documentService, PoolCardPdfService pdfService) {
        this.service = service;
        this.documentService = documentService;
        this.pdfService = pdfService;
    }

    @GetMapping
    public List<PoolCardResponse> listLegacy(@AuthenticationPrincipal OidcUser user,
                                             @RequestParam(required = false, defaultValue = "") String search) {
        return service.listLegacy(user, search);
    }

    @GetMapping("/page")
    public PoolCardPageResponse list(@AuthenticationPrincipal OidcUser user,
                                     @RequestParam(required = false, defaultValue = "") String search,
                                     @RequestParam(required = false, defaultValue = "0") int page,
                                     @RequestParam(required = false, defaultValue = "10") int size) {
        return service.list(user, search, page, size);
    }

    @GetMapping("/residents")
    public List<PoolCardResidentOptionResponse> residents(@AuthenticationPrincipal OidcUser user,
                                                           @RequestParam(required = false, defaultValue = "") String search,
                                                           @RequestParam(required = false, defaultValue = "20") int limit) {
        return service.residentOptions(user, search, limit);
    }

    @GetMapping("/settings")
    public PoolCardSettingsResponse settings(@AuthenticationPrincipal OidcUser user) { return service.settings(user); }

    @GetMapping("/{id}")
    public PoolCardResponse get(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) { return service.get(user, id); }

    @PostMapping
    public PoolCardResponse create(@AuthenticationPrincipal OidcUser user, @Valid @RequestBody PoolCardRequest request) {
        return service.create(user, request);
    }

    @PutMapping("/{id}")
    public PoolCardResponse update(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id,
                                   @Valid @RequestBody PoolCardRequest request) {
        return service.update(user, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        // Remove também o laudo do Drive para não deixar documento médico órfão.
        try { documentService.delete(user, id); } catch (org.springframework.web.server.ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 404) throw ex;
        }
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/{id}/medical-report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PoolCardResponse uploadReport(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id,
                                         @RequestPart("file") MultipartFile file) {
        PoolCard card = documentService.upload(user, id, file);
        return service.toResponse(card);
    }

    @GetMapping("/{id}/medical-report")
    public ResponseEntity<byte[]> report(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        GoogleDrivePhotoService.PhotoContent file = documentService.download(user, id);
        MediaType type;
        try { type = MediaType.parseMediaType(file.mimeType()); }
        catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.bytes());
    }

    @GetMapping("/{id}/medical-report/drive")
    public ResponseEntity<Void> reportOnGoogleDrive(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(documentService.driveViewUrl(user, id)))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @DeleteMapping("/{id}/medical-report")
    public PoolCardResponse deleteReport(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        return service.toResponse(documentService.delete(user, id));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        byte[] body = pdfService.pdf(user, id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("carteirinha-piscina.pdf", StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(body);
    }
}
