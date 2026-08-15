package com.packid.api.service;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.integration.google.GoogleDrivePhotoService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
public class RegistryPhotoService {

    private static final Logger log = LoggerFactory.getLogger(RegistryPhotoService.class);
    private static final long MAX_PHOTO_SIZE = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png");

    private final RegistryEntryRepository repository;
    private final AuthenticatedUserService authenticatedUserService;
    private final GoogleDrivePhotoService googleDrivePhotoService;

    public RegistryPhotoService(
            RegistryEntryRepository repository,
            AuthenticatedUserService authenticatedUserService,
            GoogleDrivePhotoService googleDrivePhotoService
    ) {
        this.repository = repository;
        this.authenticatedUserService = authenticatedUserService;
        this.googleDrivePhotoService = googleDrivePhotoService;
    }

    @Transactional
    public RegistryEntry upload(
            OidcUser oidcUser,
            UUID entryId,
            MultipartFile file,
            OAuth2AuthorizedClient authorizedClient
    ) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireEntry(appUser, entryId);
        byte[] bytes = validateAndRead(file);

        String currentOwner = clean(entry.getPhotoOwnerEmail());
        if (entry.getPhotoDriveFileId() != null
                && currentOwner != null
                && !currentOwner.equalsIgnoreCase(appUser.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A foto atual foi enviada por outra conta Google. Entre com a conta que enviou a foto para substituí-la.");
        }

        String oldFileId = entry.getPhotoDriveFileId();
        GoogleDrivePhotoService.DriveFile uploaded = googleDrivePhotoService.uploadPhoto(
                authorizedClient,
                appUser.getTenantId(),
                entry.getId(),
                entry.getEntryType().name(),
                file.getOriginalFilename(),
                file.getContentType(),
                bytes
        );

        entry.setPhotoDriveFileId(uploaded.id());
        entry.setPhotoMimeType(file.getContentType());
        entry.setPhotoFileName(clean(file.getOriginalFilename()));
        entry.setPhotoOwnerEmail(appUser.getEmail());
        entry.setUpdatedBy(actor(appUser));
        RegistryEntry saved = repository.save(entry);

        if (oldFileId != null && !oldFileId.equals(uploaded.id())) {
            try {
                googleDrivePhotoService.deletePhoto(authorizedClient, oldFileId);
            } catch (ResponseStatusException ex) {
                log.warn("Nova foto salva, mas não foi possível remover a foto anterior do Drive: {}", ex.getReason());
            }
        }

        return saved;
    }

    public GoogleDrivePhotoService.PhotoContent download(
            OidcUser oidcUser,
            UUID entryId,
            OAuth2AuthorizedClient authorizedClient
    ) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireEntry(appUser, entryId);
        requirePhoto(entry);
        requirePhotoOwner(appUser, entry);

        return googleDrivePhotoService.downloadPhoto(
                authorizedClient,
                entry.getPhotoDriveFileId(),
                entry.getPhotoMimeType()
        );
    }

    @Transactional
    public RegistryEntry delete(
            OidcUser oidcUser,
            UUID entryId,
            OAuth2AuthorizedClient authorizedClient
    ) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        RegistryEntry entry = requireEntry(appUser, entryId);
        requirePhoto(entry);
        requirePhotoOwner(appUser, entry);

        googleDrivePhotoService.deletePhoto(authorizedClient, entry.getPhotoDriveFileId());

        entry.setPhotoDriveFileId(null);
        entry.setPhotoMimeType(null);
        entry.setPhotoFileName(null);
        entry.setPhotoOwnerEmail(null);
        entry.setUpdatedBy(actor(appUser));
        return repository.save(entry);
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

    private void requirePhotoOwner(AppUser appUser, RegistryEntry entry) {
        String owner = clean(entry.getPhotoOwnerEmail());
        String current = clean(appUser.getEmail());
        if (owner == null || current == null || !owner.equalsIgnoreCase(current)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta foto está armazenada no Google Drive de outra conta. Entre com a conta que fez o upload para acessá-la.");
        }
    }

    private byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma foto.");
        }
        if (file.getSize() > MAX_PHOTO_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A foto deve ter no máximo 5 MB.");
        }

        String mimeType = clean(file.getContentType());
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use uma foto JPG ou PNG.");
        }

        try {
            byte[] bytes = file.getBytes();
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo enviado não é uma imagem válida.");
            }
            return bytes;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler a foto enviada.", ex);
        }
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
