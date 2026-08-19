package com.packid.api.service;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.Condominium;
import com.packid.api.domain.repository.CondominiumRepository;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.integration.google.TenantGoogleAccountService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CondominiumBrandingService {
    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final CondominiumRepository condominiumRepository;
    private final TenantGoogleAccountService googleAccountService;
    private final GoogleDrivePhotoService driveService;
    private final ImageCompressionService imageCompressionService;

    public CondominiumBrandingService(AuthenticatedUserService authenticatedUserService,
                                      AccessControlService accessControlService,
                                      CondominiumRepository condominiumRepository,
                                      TenantGoogleAccountService googleAccountService,
                                      GoogleDrivePhotoService driveService,
                                      ImageCompressionService imageCompressionService) {
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.condominiumRepository = condominiumRepository;
        this.googleAccountService = googleAccountService;
        this.driveService = driveService;
        this.imageCompressionService = imageCompressionService;
    }

    public Condominium currentForViewer(OidcUser oidcUser) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        if (!accessControlService.canOperateCondominium(user) && !accessControlService.canViewPoolCards(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem acesso ao condomínio.");
        }
        return requireCondominium(user.getTenantId());
    }

    @Transactional
    public Condominium uploadLogo(OidcUser oidcUser, MultipartFile file) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);
        Condominium condominium = requireCondominium(user.getTenantId());
        ImageCompressionService.ProcessedImage processed = imageCompressionService.process(file);
        String officialEmail = googleAccountService.getOfficialEmail(user.getTenantId());
        if (officialEmail == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Conecte a conta Google oficial do condomínio antes de enviar o logo.");
        }
        String token = googleAccountService.freshAccessToken(user.getTenantId());
        String oldFileId = condominium.getLogoDriveFileId();
        GoogleDrivePhotoService.DriveFile uploaded = driveService.uploadPhoto(
                token, user.getTenantId(), condominium.getId(), "CONDOMINIUM_LOGO",
                processed.fileName(), processed.mimeType(), processed.bytes(), null, null);
        condominium.setLogoDriveFileId(uploaded.id());
        condominium.setLogoMimeType(processed.mimeType());
        condominium.setLogoFileName(processed.fileName());
        condominium.setLogoOwnerEmail(officialEmail);
        condominium.setUpdatedBy(actor(user));
        Condominium saved = condominiumRepository.save(condominium);
        if (oldFileId != null && !oldFileId.equals(uploaded.id())) {
            try { driveService.deletePhoto(token, oldFileId); } catch (Exception ignored) { }
        }
        return saved;
    }

    @Transactional
    public Condominium deleteLogo(OidcUser oidcUser) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);
        Condominium condominium = requireCondominium(user.getTenantId());
        String fileId = clean(condominium.getLogoDriveFileId());
        if (fileId != null) {
            try { driveService.deletePhoto(googleAccountService.freshAccessToken(user.getTenantId()), fileId); }
            catch (Exception ignored) { }
        }
        condominium.setLogoDriveFileId(null);
        condominium.setLogoMimeType(null);
        condominium.setLogoFileName(null);
        condominium.setLogoOwnerEmail(null);
        condominium.setUpdatedBy(actor(user));
        return condominiumRepository.save(condominium);
    }

    public GoogleDrivePhotoService.PhotoContent download(OidcUser oidcUser) {
        Condominium condominium = currentForViewer(oidcUser);
        return downloadForTenant(condominium.getTenantId());
    }

    public GoogleDrivePhotoService.PhotoContent downloadForTenant(UUID tenantId) {
        Condominium condominium = requireCondominium(tenantId);
        if (clean(condominium.getLogoDriveFileId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Logo do condomínio não cadastrado.");
        }
        String officialEmail = googleAccountService.getOfficialEmail(tenantId);
        if (officialEmail == null || !sameEmail(officialEmail, condominium.getLogoOwnerEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O logo não está disponível na conta Google oficial do condomínio.");
        }
        return driveService.downloadPhoto(
                googleAccountService.freshAccessToken(tenantId),
                condominium.getLogoDriveFileId(), condominium.getLogoMimeType());
    }

    public Condominium requireCondominium(UUID tenantId) {
        return condominiumRepository.findAllByTenantIdAndDeletedFalse(tenantId).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado."));
    }

    private String actor(AppUser u) { return clean(u.getEmail()) == null ? "system" : u.getEmail().trim(); }
    private boolean sameEmail(String a, String b) { String x = clean(a), y = clean(b); return x != null && y != null && x.equalsIgnoreCase(y); }
    private String clean(String v) { if (v == null) return null; String c = v.trim(); return c.isBlank() ? null : c; }
}
