package com.packid.api.service;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.TenantGoogleAccount;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.integration.google.TenantGoogleAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

@Service
public class RegistryDocumentPhotoService {
    public enum DocumentKind { CPF, RG }

    private final RegistryEntryRepository repository;
    private final AuthenticatedUserService authenticatedUserService;
    private final GoogleDrivePhotoService driveService;
    private final TenantGoogleAccountService googleAccountService;
    private final ImageCompressionService imageCompressionService;

    public RegistryDocumentPhotoService(RegistryEntryRepository repository,
                                        AuthenticatedUserService authenticatedUserService,
                                        GoogleDrivePhotoService driveService,
                                        TenantGoogleAccountService googleAccountService,
                                        ImageCompressionService imageCompressionService) {
        this.repository = repository;
        this.authenticatedUserService = authenticatedUserService;
        this.driveService = driveService;
        this.googleAccountService = googleAccountService;
        this.imageCompressionService = imageCompressionService;
    }

    public DocumentKind kind(String value) {
        try {
            return DocumentKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Documento inválido. Use CPF ou RG.");
        }
    }

    @Transactional
    public RegistryEntry upload(OidcUser oidcUser, UUID entryId, DocumentKind kind, MultipartFile file,
                                OAuth2AuthorizedClient authorizedClient) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireProvider(appUser, entryId);
        ImageCompressionService.ProcessedImage processed = imageCompressionService.process(file);

        TenantGoogleAccount official = officialDriveAccount(appUser);
        String uploadOwner = official == null ? clean(appUser.getEmail()) : clean(official.getEmail());
        String token = official == null ? null : googleAccountService.freshAccessToken(appUser.getTenantId());
        String currentOwner = owner(entry, kind);
        String oldFileId = fileId(entry, kind);

        if (oldFileId != null && currentOwner != null && !sameEmail(currentOwner, uploadOwner) && !sameEmail(currentOwner, appUser.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A imagem atual do " + kind.name() + " está em outra conta Google. Reconecte a conta oficial antes de substituí-la.");
        }

        String name = kind.name().toLowerCase(Locale.ROOT) + "-" + processed.fileName();
        GoogleDrivePhotoService.DriveFile uploaded = token != null
                ? driveService.uploadPhoto(token, appUser.getTenantId(), entry.getId(), entry.getEntryType().name(), name,
                    processed.mimeType(), processed.bytes(), null, null)
                : driveService.uploadPhoto(authorizedClient, appUser.getTenantId(), entry.getId(), entry.getEntryType().name(), name,
                    processed.mimeType(), processed.bytes(), null, null);

        set(entry, kind, uploaded.id(), processed.mimeType(), name, uploadOwner);
        entry.setUpdatedBy(actor(appUser));
        RegistryEntry saved = repository.save(entry);

        if (oldFileId != null && !oldFileId.equals(uploaded.id())) {
            try {
                if (token != null && sameEmail(currentOwner, uploadOwner)) driveService.deletePhoto(token, oldFileId);
                else if (sameEmail(currentOwner, appUser.getEmail())) driveService.deletePhoto(authorizedClient, oldFileId);
            } catch (ResponseStatusException ignored) {
                // A nova imagem já foi salva; não desfazemos a operação por falha ao limpar a anterior.
            }
        }
        return saved;
    }

    public GoogleDrivePhotoService.PhotoContent download(OidcUser oidcUser, UUID entryId, DocumentKind kind,
                                                         OAuth2AuthorizedClient authorizedClient) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireProvider(appUser, entryId);
        String fileId = fileId(entry, kind);
        if (clean(fileId) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem do " + kind.name() + " não cadastrada.");

        TenantGoogleAccount official = officialDriveAccount(appUser);
        if (official != null && sameEmail(owner(entry, kind), official.getEmail())) {
            return driveService.downloadPhoto(googleAccountService.freshAccessToken(appUser.getTenantId()), fileId, mime(entry, kind));
        }
        if (!sameEmail(owner(entry, kind), appUser.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A imagem está armazenada em outra conta Google.");
        }
        return driveService.downloadPhoto(authorizedClient, fileId, mime(entry, kind));
    }

    @Transactional
    public RegistryEntry delete(OidcUser oidcUser, UUID entryId, DocumentKind kind, OAuth2AuthorizedClient authorizedClient) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireProvider(appUser, entryId);
        String fileId = fileId(entry, kind);
        if (clean(fileId) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem do " + kind.name() + " não cadastrada.");

        TenantGoogleAccount official = officialDriveAccount(appUser);
        if (official != null && sameEmail(owner(entry, kind), official.getEmail())) {
            driveService.deletePhoto(googleAccountService.freshAccessToken(appUser.getTenantId()), fileId);
        } else {
            if (!sameEmail(owner(entry, kind), appUser.getEmail())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A imagem está armazenada em outra conta Google.");
            driveService.deletePhoto(authorizedClient, fileId);
        }
        set(entry, kind, null, null, null, null);
        entry.setUpdatedBy(actor(appUser));
        return repository.save(entry);
    }

    private RegistryEntry requireProvider(AppUser appUser, UUID id) {
        RegistryEntry entry = repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prestador de serviço não encontrado."));
        if (entry.getEntryType() != RegistryEntry.EntryType.SERVICE_PROVIDER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPF/RG em imagem está disponível somente para prestadores de serviço.");
        }
        return entry;
    }

    private TenantGoogleAccount officialDriveAccount(AppUser appUser) {
        return googleAccountService.find(appUser.getTenantId())
                .filter(a -> Boolean.TRUE.equals(a.getDriveEnabled()))
                .filter(a -> clean(a.getRefreshTokenEncrypted()) != null).orElse(null);
    }

    private String fileId(RegistryEntry e, DocumentKind k) { return k == DocumentKind.CPF ? e.getCpfPhotoDriveFileId() : e.getRgPhotoDriveFileId(); }
    private String mime(RegistryEntry e, DocumentKind k) { return k == DocumentKind.CPF ? e.getCpfPhotoMimeType() : e.getRgPhotoMimeType(); }
    private String owner(RegistryEntry e, DocumentKind k) { return k == DocumentKind.CPF ? e.getCpfPhotoOwnerEmail() : e.getRgPhotoOwnerEmail(); }
    private void set(RegistryEntry e, DocumentKind k, String id, String mime, String name, String owner) {
        if (k == DocumentKind.CPF) {
            e.setCpfPhotoDriveFileId(id); e.setCpfPhotoMimeType(mime); e.setCpfPhotoFileName(name); e.setCpfPhotoOwnerEmail(owner);
        } else {
            e.setRgPhotoDriveFileId(id); e.setRgPhotoMimeType(mime); e.setRgPhotoFileName(name); e.setRgPhotoOwnerEmail(owner);
        }
    }
    private boolean sameEmail(String a, String b) { String x=clean(a), y=clean(b); return x != null && y != null && x.equalsIgnoreCase(y); }
    private String actor(AppUser u) { return clean(u.getEmail()) == null ? "system" : u.getEmail().trim(); }
    private String clean(String v) { if (v == null) return null; String c=v.trim(); return c.isBlank()?null:c; }
}
