package com.packid.api.controller.document;

import com.packid.api.controller.document.dto.ManagedDocumentResponse;
import com.packid.api.service.ManagedDocumentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/resident/documents")
public class ResidentManagedDocumentController {
    private final ManagedDocumentService service;

    public ResidentManagedDocumentController(ManagedDocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<ManagedDocumentResponse> list(HttpSession session, @RequestParam String category) {
        return service.residentList(session, category);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(HttpSession session, @PathVariable UUID id) {
        return ManagedDocumentController.response(service.residentDownload(session, id));
    }
}
