package com.packid.api.service;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.PoolCard;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.PoolCardRepository;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.integration.google.TenantGoogleAccountService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
public class PoolCardDocumentService {
    private static final long MAX_SIZE = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED = Set.of("application/pdf", "image/jpeg", "image/png");

    private final PoolCardRepository repository;
    private final PoolCardService poolCardService;
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final TenantGoogleAccountService googleAccountService;
    private final GoogleDrivePhotoService driveService;

    public PoolCardDocumentService(PoolCardRepository repository, PoolCardService poolCardService,
                                   AuthenticatedUserService authenticatedUserService,
                                   AccessControlService accessControlService,
                                   TenantGoogleAccountService googleAccountService,
                                   GoogleDrivePhotoService driveService) {
        this.repository = repository;
        this.poolCardService = poolCardService;
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.googleAccountService = googleAccountService;
        this.driveService = driveService;
    }

    @Transactional
    public PoolCard upload(OidcUser principal, UUID id, MultipartFile file) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requirePoolCardManager(user);
        PoolCard card = poolCardService.require(user.getTenantId(), id);
        validate(file);
        String officialEmail = googleAccountService.getOfficialEmail(user.getTenantId());
        if (officialEmail == null) throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                "Conecte a conta Google oficial antes de enviar o laudo médico.");
        String token = googleAccountService.freshAccessToken(user.getTenantId());
        RegistryEntry resident = card.getResident();
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String name = clean(file.getOriginalFilename()) == null ? "laudo-medico" : file.getOriginalFilename().trim();
        String oldFileId = card.getMedicalReportDriveFileId();
        try {
            GoogleDrivePhotoService.DriveFile uploaded = driveService.uploadPhoto(
                    token, user.getTenantId(), card.getId(), "POOL_CARD_REPORT",
                    name, mime, file.getBytes(), resident == null ? null : resident.getBlock(), resident == null ? null : resident.getApartment());
            card.setMedicalReportDriveFileId(uploaded.id());
            card.setMedicalReportMimeType(mime);
            card.setMedicalReportFileName(name);
            card.setMedicalReportOwnerEmail(officialEmail);
            card.setUpdatedBy(actor(user));
            PoolCard saved = repository.save(card);
            if (oldFileId != null && !oldFileId.equals(uploaded.id())) {
                try { driveService.deletePhoto(token, oldFileId); } catch (Exception ignored) { }
            }
            return saved;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo enviado.", ex);
        }
    }

    public GoogleDrivePhotoService.PhotoContent download(OidcUser principal, UUID id) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requirePoolCardManager(user);
        PoolCard card = poolCardService.require(user.getTenantId(), id);
        return downloadForCard(card);
    }

    public String driveViewUrl(OidcUser principal, UUID id) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requirePoolCardManager(user);
        PoolCard card = poolCardService.require(user.getTenantId(), id);
        String fileId = clean(card.getMedicalReportDriveFileId());
        if (fileId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Laudo médico não cadastrado.");
        }
        String officialEmail = googleAccountService.getOfficialEmail(card.getTenantId());
        if (officialEmail == null || !sameEmail(officialEmail, card.getMedicalReportOwnerEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O laudo não está disponível na conta oficial do condomínio.");
        }
        return "https://drive.google.com/file/d/" + fileId + "/view";
    }

    public GoogleDrivePhotoService.PhotoContent downloadForCard(PoolCard card) {
        if (clean(card.getMedicalReportDriveFileId()) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Laudo médico não cadastrado.");
        String officialEmail = googleAccountService.getOfficialEmail(card.getTenantId());
        if (officialEmail == null || !sameEmail(officialEmail, card.getMedicalReportOwnerEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "O laudo não está disponível na conta oficial do condomínio.");
        }
        return driveService.downloadPhoto(googleAccountService.freshAccessToken(card.getTenantId()),
                card.getMedicalReportDriveFileId(), card.getMedicalReportMimeType());
    }

    @Transactional
    public PoolCard delete(OidcUser principal, UUID id) {
        AppUser user = authenticatedUserService.requireAppUser(principal);
        accessControlService.requirePoolCardManager(user);
        PoolCard card = poolCardService.require(user.getTenantId(), id);
        String fileId = clean(card.getMedicalReportDriveFileId());
        if (fileId != null) {
            try { driveService.deletePhoto(googleAccountService.freshAccessToken(user.getTenantId()), fileId); } catch (Exception ignored) { }
        }
        card.setMedicalReportDriveFileId(null);
        card.setMedicalReportMimeType(null);
        card.setMedicalReportFileName(null);
        card.setMedicalReportOwnerEmail(null);
        card.setUpdatedBy(actor(user));
        return repository.save(card);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione o laudo médico.");
        if (file.getSize() > MAX_SIZE) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O laudo deve ter no máximo 5 MB.");
        String type = file.getContentType();
        if (type == null || !ALLOWED.contains(type.toLowerCase())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use PDF, JPG ou PNG para o laudo médico.");
    }
    private String actor(AppUser u) { return clean(u.getEmail()) == null ? "system" : u.getEmail().trim(); }
    private boolean sameEmail(String a, String b) { String x=clean(a), y=clean(b); return x != null && y != null && x.equalsIgnoreCase(y); }
    private String clean(String v) { if (v == null) return null; String c=v.trim(); return c.isBlank()?null:c; }
}
