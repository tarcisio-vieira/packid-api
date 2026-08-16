package com.packid.api.service;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.TenantGoogleAccount;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import com.packid.api.integration.google.TenantGoogleAccountService;
import com.packid.api.service.notification.UnitChangeNotificationPublisher;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class RegistryPhotoService {
    private static final Logger log = LoggerFactory.getLogger(RegistryPhotoService.class);
    private final RegistryEntryRepository repository;
    private final AuthenticatedUserService authenticatedUserService;
    private final GoogleDrivePhotoService googleDrivePhotoService;
    private final TenantGoogleAccountService googleAccountService;
    private final UnitChangeNotificationPublisher unitChangeNotificationPublisher;
    private final ImageCompressionService imageCompressionService;

    public RegistryPhotoService(
            RegistryEntryRepository repository,
            AuthenticatedUserService authenticatedUserService,
            GoogleDrivePhotoService googleDrivePhotoService,
            TenantGoogleAccountService googleAccountService,
            UnitChangeNotificationPublisher unitChangeNotificationPublisher,
            ImageCompressionService imageCompressionService
    ) {
        this.repository = repository;
        this.authenticatedUserService = authenticatedUserService;
        this.googleDrivePhotoService = googleDrivePhotoService;
        this.googleAccountService = googleAccountService;
        this.unitChangeNotificationPublisher = unitChangeNotificationPublisher;
        this.imageCompressionService = imageCompressionService;
    }

    @Transactional
    public RegistryEntry upload(OidcUser oidcUser, UUID entryId, MultipartFile file,
                                OAuth2AuthorizedClient authorizedClient) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireEntry(appUser, entryId);
        ImageCompressionService.ProcessedImage processed = imageCompressionService.process(file);

        TenantGoogleAccount official = officialDriveAccount(appUser);
        String uploadOwner = official == null ? clean(appUser.getEmail()) : clean(official.getEmail());
        String officialToken = official == null ? null : googleAccountService.freshAccessToken(appUser.getTenantId());

        String currentOwner = clean(entry.getPhotoOwnerEmail());
        if (entry.getPhotoDriveFileId() != null && currentOwner != null
                && !sameEmail(currentOwner, uploadOwner)
                && !sameEmail(currentOwner, appUser.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A foto atual está em outra conta Google. Reconecte a conta oficial ou entre com a conta que fez o upload antes de substituí-la.");
        }

        String oldFileId = entry.getPhotoDriveFileId();
        GoogleDrivePhotoService.DriveFile uploaded = officialToken != null
                ? googleDrivePhotoService.uploadPhoto(
                        officialToken, appUser.getTenantId(), entry.getId(), entry.getEntryType().name(),
                        processed.fileName(), processed.mimeType(), processed.bytes(),
                        entry.getBlock(), entry.getApartment())
                : googleDrivePhotoService.uploadPhoto(
                        authorizedClient, appUser.getTenantId(), entry.getId(), entry.getEntryType().name(),
                        processed.fileName(), processed.mimeType(), processed.bytes(),
                        entry.getBlock(), entry.getApartment());

        entry.setPhotoDriveFileId(uploaded.id());
        entry.setPhotoMimeType(processed.mimeType());
        entry.setPhotoFileName(processed.fileName());
        entry.setPhotoOwnerEmail(uploadOwner);
        entry.setUpdatedBy(actor(appUser));
        RegistryEntry saved = repository.save(entry);

        if (oldFileId != null && !oldFileId.equals(uploaded.id())) {
            try {
                if (sameEmail(currentOwner, uploadOwner) && officialToken != null) {
                    googleDrivePhotoService.deletePhoto(officialToken, oldFileId);
                } else if (sameEmail(currentOwner, appUser.getEmail())) {
                    googleDrivePhotoService.deletePhoto(authorizedClient, oldFileId);
                }
            } catch (ResponseStatusException ex) {
                log.warn("Nova foto salva, mas não foi possível remover a foto anterior do Drive: {}", ex.getReason());
            }
        }

        notifyPhotoChange(appUser, saved, oldFileId == null ? "Foto adicionada" : "Foto atualizada");
        return saved;
    }

    public GoogleDrivePhotoService.PhotoContent download(
            OidcUser oidcUser, UUID entryId, OAuth2AuthorizedClient authorizedClient) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireEntry(appUser, entryId);
        requirePhoto(entry);

        TenantGoogleAccount official = officialDriveAccount(appUser);
        if (official != null && sameEmail(entry.getPhotoOwnerEmail(), official.getEmail())) {
            return googleDrivePhotoService.downloadPhoto(
                    googleAccountService.freshAccessToken(appUser.getTenantId()),
                    entry.getPhotoDriveFileId(), entry.getPhotoMimeType());
        }

        requireCurrentUserPhotoOwner(appUser, entry);
        return googleDrivePhotoService.downloadPhoto(
                authorizedClient, entry.getPhotoDriveFileId(), entry.getPhotoMimeType());
    }

    @Transactional
    public RegistryEntry delete(OidcUser oidcUser, UUID entryId,
                                OAuth2AuthorizedClient authorizedClient) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireEntry(appUser, entryId);
        requirePhoto(entry);

        TenantGoogleAccount official = officialDriveAccount(appUser);
        if (official != null && sameEmail(entry.getPhotoOwnerEmail(), official.getEmail())) {
            googleDrivePhotoService.deletePhoto(
                    googleAccountService.freshAccessToken(appUser.getTenantId()), entry.getPhotoDriveFileId());
        } else {
            requireCurrentUserPhotoOwner(appUser, entry);
            googleDrivePhotoService.deletePhoto(authorizedClient, entry.getPhotoDriveFileId());
        }

        entry.setPhotoDriveFileId(null);
        entry.setPhotoMimeType(null);
        entry.setPhotoFileName(null);
        entry.setPhotoOwnerEmail(null);
        entry.setUpdatedBy(actor(appUser));
        RegistryEntry saved = repository.save(entry);
        notifyPhotoChange(appUser, saved, "Foto removida");
        return saved;
    }

    private TenantGoogleAccount officialDriveAccount(AppUser appUser) {
        return googleAccountService.find(appUser.getTenantId())
                .filter(account -> Boolean.TRUE.equals(account.getDriveEnabled()))
                .filter(account -> clean(account.getRefreshTokenEncrypted()) != null)
                .orElse(null);
    }

    private void notifyPhotoChange(AppUser appUser, RegistryEntry entry, String action) {
        if (entry.getEntryType() != RegistryEntry.EntryType.RESIDENT
                && entry.getEntryType() != RegistryEntry.EntryType.BICYCLE
                && entry.getEntryType() != RegistryEntry.EntryType.PET
                && entry.getEntryType() != RegistryEntry.EntryType.VEHICLE) return;

        java.util.List<String> extras = entry.getEntryType() == RegistryEntry.EntryType.RESIDENT
                && clean(entry.getEmail()) != null
                ? java.util.List.of(entry.getEmail().trim()) : java.util.List.of();
        unitChangeNotificationPublisher.publish(
                appUser.getTenantId(), entry.getBlock(), entry.getApartment(), extras,
                "REGISTRY_PHOTO_CHANGED", action,
                action + " no cadastro de \"" + entry.getName() + "\".", actor(appUser));
    }

    private RegistryEntry requireEntry(AppUser appUser, UUID entryId) {
        return repository.findByTenantIdAndIdAndDeletedFalse(appUser.getTenantId(), entryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro não encontrado."));
    }

    private void requirePhoto(RegistryEntry entry) {
        if (entry.getPhotoDriveFileId() == null || entry.getPhotoDriveFileId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Este cadastro não possui foto.");
        }
    }

    private void requireCurrentUserPhotoOwner(AppUser appUser, RegistryEntry entry) {
        if (!sameEmail(entry.getPhotoOwnerEmail(), appUser.getEmail())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta foto está armazenada em outra conta Google. Conecte a conta oficial do condomínio em Configurações.");
        }
    }

    private boolean sameEmail(String left, String right) {
        String a = clean(left), b = clean(right);
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String actor(AppUser appUser) {
        return clean(appUser.getEmail()) == null ? "system" : appUser.getEmail().trim();
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
