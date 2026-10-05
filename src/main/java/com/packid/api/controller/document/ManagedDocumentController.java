package com.packid.api.controller.document;

import com.packid.api.controller.document.dto.ManagedDocumentResponse;
import com.packid.api.service.ManagedDocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/managed-documents")
public class ManagedDocumentController {
    private final ManagedDocumentService service;

    public ManagedDocumentController(ManagedDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<ManagedDocumentResponse> list(@AuthenticationPrincipal OidcUser user, @RequestParam String category) {
        return service.staffList(user, category);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ManagedDocumentResponse upload(
            @AuthenticationPrincipal OidcUser user,
            @RequestParam String category,
            @RequestParam String displayName,
            @RequestPart("file") MultipartFile file
    ) {
        return service.upload(user, category, displayName, file);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        return response(service.staffDownload(user, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal OidcUser user, @PathVariable UUID id) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    static ResponseEntity<byte[]> response(ManagedDocumentService.DownloadedDocument file) {
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(file.mimeType()); }
        catch (Exception ex) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.bytes());
    }
}
