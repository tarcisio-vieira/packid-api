package com.packid.api.service;

import com.packid.api.controller.document.dto.ManagedDocumentResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.ManagedDocument;
import com.packid.api.domain.repository.ManagedDocumentRepository;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.integration.google.TenantGoogleAccountService;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ManagedDocumentService {
    private static final long MAX_FILE_SIZE = 30L * 1024L * 1024L;

    private final ManagedDocumentRepository repository;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final ResidentSessionService residentSessionService;
    private final TenantGoogleAccountService googleAccountService;
    private final GoogleDrivePhotoService driveService;

    public ManagedDocumentService(
            ManagedDocumentRepository repository,
            AuthenticatedUserService authenticatedUserService,
            AccessControlService accessControlService,
            ResidentSessionService residentSessionService,
            TenantGoogleAccountService googleAccountService,
            GoogleDrivePhotoService driveService
    ) {
        this.repository = repository;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.residentSessionService = residentSessionService;
        this.googleAccountService = googleAccountService;
        this.driveService = driveService;
    }

    @Transactional
    public List<ManagedDocumentResponse> staffList(OidcUser oidcUser, String category) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireOperationalUser(user);
        return list(user.getTenantId(), parseCategory(category));
    }

    @Transactional
    public List<ManagedDocumentResponse> residentList(HttpSession session, String category) {
        var context = residentSessionService.requirePortalContext(session);
        return list(context.tenant().getId(), parseCategory(category));
    }

    @Transactional
    public ManagedDocumentResponse upload(OidcUser oidcUser, String category, String displayName, MultipartFile file) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);
        ManagedDocument.Category parsedCategory = parseCategory(category);
        validateFile(file);
        String title = clean(displayName);
        if (title == null || title.length() > 220) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome de identificação com até 220 caracteres.");
        }

        String original = clean(file.getOriginalFilename());
        if (original == null) original = "arquivo";
        if (original.length() > 500) original = original.substring(original.length() - 500);
        String mime = clean(file.getContentType());
        if (mime == null) mime = "application/octet-stream";
        String extension = extension(original);

        ManagedDocument row = new ManagedDocument();
        row.setTenantId(user.getTenantId());
        row.setCategory(parsedCategory);
        row.setDisplayName(title);
        row.setOriginalFileName(original);
        row.setMimeType(mime);
        row.setFileExtension(extension);
        row.setFileSize(file.getSize());
        row.setDriveFileId("pending");
        row.setCreatedBy(actor(user));
        row = repository.save(row);

        try {
            String token = driveAccessToken(user.getTenantId());
            var uploaded = driveService.uploadTenantDocument(
                    token,
                    user.getTenantId(),
                    row.getId(),
                    folderName(parsedCategory),
                    original,
                    mime,
                    file.getBytes()
            );
            row.setDriveFileId(uploaded.id());
            row = repository.save(row);
            return toResponse(row);
        } catch (IOException ex) {
            repository.delete(row);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível ler o arquivo enviado.", ex);
        } catch (RuntimeException ex) {
            repository.delete(row);
            throw ex;
        }
    }

    @Transactional
    public DownloadedDocument staffDownload(OidcUser oidcUser, UUID id) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireOperationalUser(user);
        return download(user.getTenantId(), id);
    }

    @Transactional
    public DownloadedDocument residentDownload(HttpSession session, UUID id) {
        var context = residentSessionService.requirePortalContext(session);
        return download(context.tenant().getId(), id);
    }

    @Transactional
    public void delete(OidcUser oidcUser, UUID id) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);
        ManagedDocument row = repository.findByTenantIdAndIdAndDeletedFalse(user.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        try {
            String token = driveAccessToken(user.getTenantId());
            driveService.deletePhoto(token, row.getDriveFileId());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 404) throw ex;
        }
        row.setDeleted(true);
        row.setDeletedAt(LocalDateTime.now());
        row.setDeletedBy(actor(user));
        repository.save(row);
    }

    private List<ManagedDocumentResponse> list(UUID tenantId, ManagedDocument.Category category) {
        return repository.findAllByTenantIdAndCategoryAndDeletedFalseOrderByDisplayNameAsc(tenantId, category)
                .stream().map(this::toResponse).toList();
    }

    private DownloadedDocument download(UUID tenantId, UUID id) {
        ManagedDocument row = repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado."));
        String token = driveAccessToken(tenantId);
        var content = driveService.downloadPhoto(token, row.getDriveFileId(), row.getMimeType());
        return new DownloadedDocument(content.bytes(), clean(content.mimeType()) == null ? row.getMimeType() : content.mimeType(), row.getOriginalFileName());
    }


    private String driveAccessToken(UUID tenantId) {
        var account = googleAccountService.requireConnected(tenantId);
        if (!Boolean.TRUE.equals(account.getDriveEnabled())) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Habilite o Google Drive da conta oficial do condomínio em Configurações.");
        }
        return googleAccountService.freshAccessToken(tenantId);
    }

    private ManagedDocumentResponse toResponse(ManagedDocument row) {
        return new ManagedDocumentResponse(
                row.getId(), row.getCategory().name(), row.getDisplayName(), row.getOriginalFileName(),
                row.getMimeType(), row.getFileExtension(), row.getFileSize() == null ? 0 : row.getFileSize(),
                row.getCreatedAt(), row.getCreatedBy()
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um arquivo.");
        if (file.getSize() > MAX_FILE_SIZE) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo deve ter no máximo 30 MB.");
    }

    private ManagedDocument.Category parseCategory(String value) {
        String normalized = clean(value);
        if (normalized == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a categoria do arquivo.");
        normalized = normalized.toUpperCase(Locale.ROOT);
        if ("RI".equals(normalized) || "REGULAMENTO_INTERNO".equals(normalized)) return ManagedDocument.Category.INTERNAL_REGULATION;
        try { return ManagedDocument.Category.valueOf(normalized); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria de documento inválida."); }
    }

    private String folderName(ManagedDocument.Category category) {
        return category == ManagedDocument.Category.INTERNAL_REGULATION ? "RI - Regulamento Interno" : "Biblioteca";
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) return "";
        String ext = name.substring(index + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return ext.length() > 24 ? ext.substring(0, 24) : ext;
    }

    private String actor(AppUser user) {
        String value = clean(user.getEmail());
        if (value == null) value = clean(user.getFullName());
        return value == null ? "system" : value;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    public record DownloadedDocument(byte[] bytes, String mimeType, String fileName) {}
}
