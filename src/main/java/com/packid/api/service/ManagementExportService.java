package com.packid.api.service;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.ServiceCompany;
import com.packid.api.domain.model.SpaceAccessRequest;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.repository.ServiceCompanyRepository;
import com.packid.api.domain.repository.SpaceAccessRequestRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ManagementExportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final RegistryEntryRepository registryEntryRepository;
    private final ServiceCompanyRepository serviceCompanyRepository;
    private final SpaceAccessRequestRepository spaceAccessRequestRepository;

    public ManagementExportService(
            AuthenticatedUserService authenticatedUserService,
            AccessControlService accessControlService,
            RegistryEntryRepository registryEntryRepository,
            ServiceCompanyRepository serviceCompanyRepository,
            SpaceAccessRequestRepository spaceAccessRequestRepository
    ) {
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.registryEntryRepository = registryEntryRepository;
        this.serviceCompanyRepository = serviceCompanyRepository;
        this.spaceAccessRequestRepository = spaceAccessRequestRepository;
    }

    public ExportFile exportRegistry(OidcUser oidcUser, RegistryEntry.EntryType type) {
        AppUser user = exportUser(oidcUser);
        List<RegistryEntry> entries = registryEntryRepository
                .findAllByTenantIdAndEntryTypeAndDeletedFalseOrderByNameAsc(user.getTenantId(), type);

        String[] headers = {
                "Nome", "Tipo", "Bloco", "Apartamento", "Proprietário", "Documento", "Telefone", "E-mail",
                "Data de nascimento", "Profissão", "PNE", "Empresa", "Responsável / proprietário", "Marca", "Modelo",
                "Cor", "Identificador / placa", "Espécie", "Raça", "Porte", "Vaga", "Vaga alugada / cedida",
                "Observação da vaga", "Observações", "Usuário do morador", "Foto", "Foto do documento", "Status",
                "Criado em", "Atualizado em", "Criado por", "Atualizado por"
        };

        List<List<String>> rows = entries.stream().map(entry -> List.of(
                text(entry.getName()),
                registryTypeLabel(entry.getEntryType()),
                text(entry.getBlock()),
                text(entry.getApartment()),
                yesNo(entry.getUnitOwner()),
                text(entry.getDocument()),
                text(entry.getPhone()),
                text(entry.getEmail()),
                format(entry.getBirthDate()),
                text(entry.getProfession()),
                yesNo(entry.getPne()),
                text(entry.getCompany()),
                text(entry.getOwnerName()),
                text(entry.getBrand()),
                text(entry.getModel()),
                text(entry.getColor()),
                text(entry.getIdentifier()),
                text(entry.getSpecies()),
                text(entry.getBreed()),
                text(entry.getPetSize()),
                text(entry.getParkingSpace()),
                yesNo(entry.getParkingSpaceRented()),
                text(entry.getParkingSpaceRentalNotes()),
                text(entry.getNotes()),
                text(entry.getResidentUsername()),
                present(entry.getPhotoDriveFileId()),
                present(entry.getDocumentPhotoDriveFileId()),
                Boolean.FALSE.equals(entry.getActive()) ? "Inativo" : "Ativo",
                format(entry.getCreatedAt()),
                format(entry.getUpdatedAt()),
                text(entry.getCreatedBy()),
                text(entry.getUpdatedBy())
        )).toList();

        String label = registryTypeFileLabel(type);
        return workbook(label + "_" + FILE_TIME.format(LocalDateTime.now()) + ".xlsx", registryTypeLabel(type), headers, rows);
    }

    public ExportFile exportServiceCompanies(OidcUser oidcUser) {
        AppUser user = exportUser(oidcUser);
        List<ServiceCompany> companies = serviceCompanyRepository
                .findAllByTenantIdAndDeletedFalseOrderByNameAsc(user.getTenantId());

        String[] headers = {
                "Razão social / Nome", "Nome fantasia", "CNPJ / Documento", "Telefone", "E-mail", "Responsável / Contato",
                "Endereço", "Cidade", "Estado", "CEP", "Observações", "Status", "Criado em", "Atualizado em", "Criado por", "Atualizado por"
        };
        List<List<String>> rows = companies.stream().map(company -> List.of(
                text(company.getName()),
                text(company.getTradeName()),
                text(company.getDocumentNumber()),
                text(company.getPhone()),
                text(company.getEmail()),
                text(company.getContactName()),
                text(company.getAddressLine()),
                text(company.getCity()),
                text(company.getState()),
                text(company.getZipCode()),
                text(company.getNotes()),
                Boolean.FALSE.equals(company.getActive()) ? "Inativa" : "Ativa",
                format(company.getCreatedAt()),
                format(company.getUpdatedAt()),
                text(company.getCreatedBy()),
                text(company.getUpdatedBy())
        )).toList();

        return workbook("empresas_" + FILE_TIME.format(LocalDateTime.now()) + ".xlsx", "Empresas", headers, rows);
    }

    public ExportFile exportSpaceAccess(OidcUser oidcUser) {
        AppUser user = exportUser(oidcUser);
        List<SpaceAccessRequest> requests = spaceAccessRequestRepository
                .findAllByTenantIdAndDeletedFalseOrderByRequestedAtDesc(user.getTenantId());

        String[] headers = {
                "Área", "Bloco", "Apartamento", "Status", "Solicitado em", "Liberado em", "Pedido de devolução",
                "Encerrado em", "Liberado por", "Encerrado por", "Observações"
        };
        List<List<String>> rows = requests.stream().map(request -> List.of(
                spaceTypeLabel(request.getSpaceType()),
                text(request.getBlock()),
                text(request.getApartment()),
                spaceStatusLabel(request.getStatus()),
                format(request.getRequestedAt()),
                format(request.getReleasedAt()),
                format(request.getReturnRequestedAt()),
                format(request.getCompletedAt()),
                text(request.getReleasedBy()),
                text(request.getCompletedBy()),
                text(request.getNotes())
        )).toList();

        return workbook("area-lazer_" + FILE_TIME.format(LocalDateTime.now()) + ".xlsx", "Área de lazer", headers, rows);
    }

    private AppUser exportUser(OidcUser oidcUser) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireAdministrativeUser(user);
        return user;
    }

    private ExportFile workbook(String fileName, String sheetName, String[] headers, List<List<String>> data) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            sheet.createFreezePane(0, 1);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            int[] maxLengths = new int[headers.length];
            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
                maxLengths[col] = headers[col].length();
            }

            int rowIndex = 1;
            for (List<String> values : data) {
                Row row = sheet.createRow(rowIndex++);
                for (int col = 0; col < headers.length; col++) {
                    String value = col < values.size() ? text(values.get(col)) : "";
                    row.createCell(col).setCellValue(value);
                    maxLengths[col] = Math.max(maxLengths[col], longestLine(value));
                }
            }

            if (headers.length > 0) {
                sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.length - 1));
            }
            for (int col = 0; col < headers.length; col++) {
                int chars = Math.max(10, Math.min(maxLengths[col] + 2, 45));
                sheet.setColumnWidth(col, chars * 256);
            }

            workbook.write(output);
            return new ExportFile(fileName, output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível gerar o arquivo Excel.", exception);
        }
    }

    private int longestLine(String value) {
        int max = 0;
        for (String line : value.split("\\R", -1)) max = Math.max(max, line.length());
        return max;
    }

    private String registryTypeLabel(RegistryEntry.EntryType type) {
        return switch (type) {
            case RESIDENT -> "Condôminos";
            case SERVICE_PROVIDER -> "Prestadores de serviço";
            case DELIVERY_PERSON -> "Entregadores";
            case VISITOR -> "Visitantes";
            case BICYCLE -> "Bicicletas";
            case PET -> "Pets";
            case VEHICLE -> "Veículos";
        };
    }

    private String registryTypeFileLabel(RegistryEntry.EntryType type) {
        return switch (type) {
            case RESIDENT -> "condominos";
            case SERVICE_PROVIDER -> "prestadores-servico";
            case DELIVERY_PERSON -> "entregadores";
            case VISITOR -> "visitantes";
            case BICYCLE -> "bicicletas";
            case PET -> "pets";
            case VEHICLE -> "veiculos";
        };
    }

    private String spaceTypeLabel(SpaceAccessRequest.SpaceType type) {
        return switch (type) {
            case PLAYROOM -> "Brinquedoteca";
            case GAMES_ROOM -> "Sala de Jogos";
            case GYM -> "Academia";
            case SAUNA -> "Sauna";
        };
    }

    private String spaceStatusLabel(SpaceAccessRequest.Status status) {
        return switch (status) {
            case REQUESTED_PICKUP -> "Aguardando liberação";
            case IN_USE -> "Em uso";
            case REQUESTED_RETURN -> "Aguardando devolução";
            case COMPLETED -> "Finalizado";
            case CANCELLED -> "Cancelado";
        };
    }

    private String present(String value) {
        return value == null || value.isBlank() ? "Não" : "Sim";
    }

    private String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "Sim" : "Não";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String format(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    private String format(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    public record ExportFile(String fileName, byte[] content) {}
}
