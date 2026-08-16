package com.packid.api.integration.google;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleDrivePhotoService {

    private static final String DRIVE_API = "https://www.googleapis.com";
    private static final String ROOT_FOLDER_NAME = "VSGI-Condominium";
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GoogleDrivePhotoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(DRIVE_API)
                .build();
    }

    public DriveFile uploadPhoto(
            OAuth2AuthorizedClient authorizedClient,
            UUID tenantId,
            UUID registryEntryId,
            String entryType,
            String originalFilename,
            String mimeType,
            byte[] bytes,
            String block,
            String apartment
    ) {
        return uploadPhoto(accessToken(authorizedClient), tenantId, registryEntryId, entryType,
                originalFilename, mimeType, bytes, block, apartment);
    }

    public DriveFile uploadPhoto(
            String accessToken,
            UUID tenantId,
            UUID registryEntryId,
            String entryType,
            String originalFilename,
            String mimeType,
            byte[] bytes,
            String block,
            String apartment
    ) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token do Google Drive não disponível.");
        }
        String folderId;
        if ("SERVICE_PROVIDER".equalsIgnoreCase(entryType)) {
            folderId = findOrCreateRegistryPersonFolder(
                    accessToken, registryEntryId, "Service Providers", "service-providers",
                    "Provider ", "vsgiServiceProviderId");
        } else if ("DELIVERY_PERSON".equalsIgnoreCase(entryType)) {
            folderId = findOrCreateRegistryPersonFolder(
                    accessToken, registryEntryId, "Delivery People", "delivery-people",
                    "Delivery Person ", "vsgiDeliveryPersonId");
        } else {
            folderId = findOrCreatePhotoFolder(accessToken, block, apartment);
        }

        String safeName = buildFileName(registryEntryId, originalFilename, mimeType);
        Map<String, Object> metadata = Map.of(
                "name", safeName,
                "parents", List.of(folderId),
                "appProperties", Map.of(
                        "packidTenantId", tenantId.toString(),
                        "packidRegistryEntryId", registryEntryId.toString(),
                        "packidEntryType", entryType
                )
        );

        try {
            String boundary = "packid-" + UUID.randomUUID();
            byte[] body = multipartRelatedBody(boundary, metadata, mimeType, bytes);

            DriveFile created = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/upload/drive/v3/files")
                            .queryParam("uploadType", "multipart")
                            .queryParam("fields", "id,name,mimeType,size")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary))
                    .body(body)
                    .retrieve()
                    .body(DriveFile.class);

            if (created == null || created.id() == null || created.id().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "O Google Drive não retornou o identificador da foto.");
            }
            return created;
        } catch (RestClientResponseException ex) {
            throw driveException("Não foi possível enviar a foto para o Google Drive.", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Não foi possível preparar a foto para envio ao Google Drive.", ex);
        }
    }

    public PhotoContent downloadPhoto(
            OAuth2AuthorizedClient authorizedClient,
            String driveFileId,
            String fallbackMimeType
    ) {
        return downloadPhoto(accessToken(authorizedClient), driveFileId, fallbackMimeType);
    }

    public PhotoContent downloadPhoto(
            String accessToken,
            String driveFileId,
            String fallbackMimeType
    ) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token do Google Drive não disponível.");
        }
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/drive/v3/files/{fileId}")
                            .queryParam("alt", "media")
                            .build(driveFileId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] body = response.getBody();
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Foto não encontrada no Google Drive.");
            }

            MediaType contentType = response.getHeaders().getContentType();
            String resolvedMimeType = contentType != null
                    ? contentType.toString()
                    : fallbackMimeType;

            return new PhotoContent(body, resolvedMimeType);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Foto não encontrada no Google Drive.");
            }
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A conta Google logada não possui acesso a esta foto no Drive.");
            }
            throw driveException("Não foi possível carregar a foto do Google Drive.", ex);
        }
    }

    public void deletePhoto(OAuth2AuthorizedClient authorizedClient, String driveFileId) {
        deletePhoto(accessToken(authorizedClient), driveFileId);
    }

    public void deletePhoto(String accessToken, String driveFileId) {
        if (driveFileId == null || driveFileId.isBlank()) return;
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token do Google Drive não disponível.");
        }
        try {
            restClient.delete()
                    .uri("/drive/v3/files/{fileId}", driveFileId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return;
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "A conta Google logada não possui permissão para excluir esta foto do Drive.");
            }
            throw driveException("Não foi possível excluir a foto do Google Drive.", ex);
        }
    }

    private String findOrCreateRegistryPersonFolder(
            String accessToken,
            UUID registryEntryId,
            String sectionName,
            String sectionKey,
            String personPrefix,
            String personProperty
    ) {
        String rootFolderId = findOrCreateFolder(accessToken, null, ROOT_FOLDER_NAME, "vsgiFolder", "condominium");
        String sectionFolderId = findOrCreateFolder(accessToken, rootFolderId, sectionName, "vsgiSection", sectionKey);
        return findOrCreateFolder(accessToken, sectionFolderId, personPrefix + registryEntryId,
                personProperty, registryEntryId.toString());
    }

    private String findOrCreatePhotoFolder(String accessToken, String block, String apartment) {
        String rootFolderId = findOrCreateFolder(
                accessToken,
                null,
                ROOT_FOLDER_NAME,
                "vsgiFolder",
                "condominium"
        );

        String blockValue = folderValue(block, "Unassigned");
        String blockFolderId = findOrCreateFolder(
                accessToken,
                rootFolderId,
                "Block " + blockValue,
                "vsgiBlock",
                blockValue
        );

        String apartmentValue = folderValue(apartment, "Unassigned");
        return findOrCreateFolder(
                accessToken,
                blockFolderId,
                "Apartment " + apartmentValue,
                "vsgiApartment",
                apartmentValue
        );
    }

    private String findOrCreateFolder(
            String accessToken,
            String parentFolderId,
            String folderName,
            String appPropertyKey,
            String appPropertyValue
    ) {
        String q = "mimeType='" + FOLDER_MIME_TYPE + "' and trashed=false " +
                "and appProperties has { key='" + escapeDriveQuery(appPropertyKey) +
                "' and value='" + escapeDriveQuery(appPropertyValue) + "' }";

        if (parentFolderId != null && !parentFolderId.isBlank()) {
            q += " and '" + escapeDriveQuery(parentFolderId) + "' in parents";
        }
        final String query = q;

        try {
            DriveFileList list = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/drive/v3/files")
                            // A sintaxe de busca do Drive usa chaves em appProperties.
                            // O valor é fornecido como variável para impedir que o Spring
                            // interprete essas chaves como placeholders da URI.
                            .queryParam("q", "{q}")
                            .queryParam("spaces", "drive")
                            .queryParam("pageSize", 10)
                            .queryParam("fields", "files(id,name,mimeType)")
                            .build(query))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(DriveFileList.class);

            if (list != null && list.files() != null && !list.files().isEmpty()) {
                return list.files().get(0).id();
            }

            Map<String, Object> metadata;
            if (parentFolderId == null || parentFolderId.isBlank()) {
                metadata = Map.of(
                        "name", folderName,
                        "mimeType", FOLDER_MIME_TYPE,
                        "appProperties", Map.of(appPropertyKey, appPropertyValue)
                );
            } else {
                metadata = Map.of(
                        "name", folderName,
                        "mimeType", FOLDER_MIME_TYPE,
                        "parents", List.of(parentFolderId),
                        "appProperties", Map.of(appPropertyKey, appPropertyValue)
                );
            }

            DriveFile folder = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/drive/v3/files")
                            .queryParam("fields", "id,name,mimeType")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(metadata)
                    .retrieve()
                    .body(DriveFile.class);

            if (folder == null || folder.id() == null || folder.id().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "O Google Drive não retornou o identificador da pasta de fotos.");
            }
            return folder.id();
        } catch (RestClientResponseException ex) {
            throw driveException("Não foi possível acessar a pasta de fotos no Google Drive.", ex);
        }
    }

    private String folderValue(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.trim()
                .replace('/', '-')
                .replace('\\', '-')
                .replaceAll("[\\r\\n\\t]", " ")
                .replaceAll("\\s+", " ");
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private String escapeDriveQuery(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private byte[] multipartRelatedBody(
            String boundary,
            Map<String, Object> metadata,
            String mimeType,
            byte[] bytes
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, "--" + boundary + "\r\n");
        write(output, "Content-Type: application/json; charset=UTF-8\r\n\r\n");
        output.write(objectMapper.writeValueAsBytes(metadata));
        write(output, "\r\n--" + boundary + "\r\n");
        write(output, "Content-Type: " + mimeType + "\r\n\r\n");
        output.write(bytes);
        write(output, "\r\n--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private void write(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private String accessToken(OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Não foi possível obter autorização do Google Drive. Entre novamente com o Google.");
        }
        return authorizedClient.getAccessToken().getTokenValue();
    }

    private String buildFileName(UUID registryEntryId, String originalFilename, String mimeType) {
        String extension = extension(originalFilename, mimeType);
        String base = originalFilename == null ? "foto" : originalFilename;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        base = base.replaceAll("[^A-Za-z0-9_-]", "-");
        if (base.isBlank()) base = "foto";
        if (base.length() > 40) base = base.substring(0, 40);
        return "vsgi-" + registryEntryId + "-" + base + "-" + System.currentTimeMillis() + extension;
    }

    private String extension(String originalFilename, String mimeType) {
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                String ext = originalFilename.substring(dot)
                        .replaceAll("[^A-Za-z0-9.]", "")
                        .toLowerCase();
                if (ext.length() <= 8) return ext;
            }
        }
        if ("image/png".equalsIgnoreCase(mimeType)) return ".png";
        return ".jpg";
    }

    private ResponseStatusException driveException(String message, RestClientResponseException ex) {
        if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
            return new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "A conta Google precisa autorizar o PackID a gravar fotos no Google Drive. " +
                            "Saia do sistema, entre novamente com o Google e aceite a permissão solicitada.");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, ex);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DriveFile(String id, String name, String mimeType, String size) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DriveFileList(List<DriveFile> files) {}

    public record PhotoContent(byte[] bytes, String mimeType) {}
}
