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
    public enum DocumentKind { DOCUMENT }

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
        String normalized = clean(value);
        if (normalized != null) {
            normalized = normalized.toLowerCase(Locale.ROOT);
            // "cpf" e "rg" continuam aceitos como aliases temporários para compatibilidade
            // com versões anteriores do front-end durante a atualização do ambiente.
            if (normalized.equals("document") || normalized.equals("cpf") || normalized.equals("rg")) {
                return DocumentKind.DOCUMENT;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Documento inválido. Use document.");
    }

    @Transactional
    public RegistryEntry upload(OidcUser oidcUser, UUID entryId, DocumentKind kind, MultipartFile file,
                                OAuth2AuthorizedClient authorizedClient) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireSupportedEntry(appUser, entryId);
        ImageCompressionService.ProcessedImage processed = imageCompressionService.process(file);

        TenantGoogleAccount official = officialDriveAccount(appUser);
        String uploadOwner = official == null ? clean(appUser.getEmail()) : clean(official.getEmail());
        String token = official == null ? null : googleAccountService.freshAccessToken(appUser.getTenantId());
        String currentOwner = owner(entry, kind);
        String oldFileId = fileId(entry, kind);

        if (oldFileId != null && currentOwner != null && !sameEmail(currentOwner, uploadOwner) && !sameEmail(currentOwner, appUser.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A imagem atual do documento está em outra conta Google. Reconecte a conta oficial antes de substituí-la.");
        }

        String name = "documento-" + processed.fileName();
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
        RegistryEntry entry = requireSupportedEntry(appUser, entryId);
        String fileId = fileId(entry, kind);
        if (clean(fileId) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem do documento não cadastrada.");

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
        RegistryEntry entry = requireSupportedEntry(appUser, entryId);
        String fileId = fileId(entry, kind);
        if (clean(fileId) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem do documento não cadastrada.");

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

    private RegistryEntry requireSupportedEntry(AppUser appUser, UUID id) {
        RegistryEntry entry = repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
        if (entry.getEntryType() != RegistryEntry.EntryType.SERVICE_PROVIDER
                && entry.getEntryType() != RegistryEntry.EntryType.DELIVERY_PERSON) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A foto de documento está disponível para prestadores de serviço e entregadores.");
        }
        return entry;
    }

    private TenantGoogleAccount officialDriveAccount(AppUser appUser) {
        return googleAccountService.find(appUser.getTenantId())
                .filter(a -> Boolean.TRUE.equals(a.getDriveEnabled()))
                .filter(a -> clean(a.getRefreshTokenEncrypted()) != null).orElse(null);
    }

    private String fileId(RegistryEntry e, DocumentKind k) { return e.getDocumentPhotoDriveFileId(); }
    private String mime(RegistryEntry e, DocumentKind k) { return e.getDocumentPhotoMimeType(); }
    private String owner(RegistryEntry e, DocumentKind k) { return e.getDocumentPhotoOwnerEmail(); }
    private void set(RegistryEntry e, DocumentKind k, String id, String mime, String name, String owner) {
        e.setDocumentPhotoDriveFileId(id);
        e.setDocumentPhotoMimeType(mime);
        e.setDocumentPhotoFileName(name);
        e.setDocumentPhotoOwnerEmail(owner);
    }
    private boolean sameEmail(String a, String b) { String x=clean(a), y=clean(b); return x != null && y != null && x.equalsIgnoreCase(y); }
    private String actor(AppUser u) { return clean(u.getEmail()) == null ? "system" : u.getEmail().trim(); }
    private String clean(String v) { if (v == null) return null; String c=v.trim(); return c.isBlank()?null:c; }
}
