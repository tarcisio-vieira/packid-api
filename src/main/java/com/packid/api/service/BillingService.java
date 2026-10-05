package com.packid.api.service;

import com.packid.api.controller.billing.dto.BillingChargeCreateRequest;
import com.packid.api.controller.billing.dto.BillingChargeResponse;
import com.packid.api.domain.model.*;
import com.packid.api.domain.repository.ApartmentOccupancyRepository;
import com.packid.api.domain.repository.BillingChargeRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.integration.google.GoogleGmailService;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BillingService {
    private static final DateTimeFormatter DATE_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AuthenticatedUserService authenticatedUserService;
    private final AccessControlService accessControlService;
    private final BillingChargeRepository repository;
    private final ApartmentOccupancyRepository occupancyRepository;
    private final RegistryEntryRepository registryEntryRepository;
    private final ResidentSessionService residentSessionService;
    private final GoogleGmailService gmailService;
    private final BankIntegrationSettingsService bankSettingsService;

    public BillingService(
            AuthenticatedUserService authenticatedUserService,
            AccessControlService accessControlService,
            BillingChargeRepository repository,
            ApartmentOccupancyRepository occupancyRepository,
            RegistryEntryRepository registryEntryRepository,
            ResidentSessionService residentSessionService,
            GoogleGmailService gmailService,
            BankIntegrationSettingsService bankSettingsService
    ) {
        this.authenticatedUserService = authenticatedUserService;
        this.accessControlService = accessControlService;
        this.repository = repository;
        this.occupancyRepository = occupancyRepository;
        this.registryEntryRepository = registryEntryRepository;
        this.residentSessionService = residentSessionService;
        this.gmailService = gmailService;
        this.bankSettingsService = bankSettingsService;
    }

    public List<BillingChargeResponse> listForAdministration(OidcUser oidcUser) {
        AppUser user = requireManager(oidcUser);
        return repository.findAllByTenantIdAndDeletedFalseOrderByDueDateDesc(user.getTenantId())
                .stream().map(c -> toResponse(user.getTenantId(), c)).toList();
    }

    public List<BillingChargeResponse> listForResident(HttpSession session) {
        ResidentSessionService.ResidentContext ctx = residentSessionService.requirePortalContext(session);
        return repository.findAllByTenantIdAndOccupancyIdAndDeletedFalseOrderByDueDateDesc(
                        ctx.tenant().getId(), ctx.occupancy().getId())
                .stream().map(c -> toResponse(ctx.tenant().getId(), c)).toList();
    }

    @Transactional
    public BillingChargeResponse createManual(OidcUser oidcUser, BillingChargeCreateRequest request) {
        AppUser user = requireManager(oidcUser);
        ApartmentOccupancy occupancy = occupancyRepository.findFirstByTenantIdAndBlockIgnoreCaseAndApartmentIgnoreCaseAndStatusAndDeletedFalse(
                        user.getTenantId(), request.block(), request.apartment(), ApartmentOccupancy.Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Não existe ocupação ativa para a unidade informada."));

        BillingCharge charge = new BillingCharge();
        charge.setTenantId(user.getTenantId());
        charge.setOccupancyId(occupancy.getId());
        charge.setBankProvider(clean(request.bankProvider()));
        charge.setExternalId(clean(request.externalId()));
        charge.setReferenceNumber(clean(request.referenceNumber()));
        charge.setDescription(clean(request.description()));
        charge.setNominalValue(request.nominalValue());
        charge.setIssueDate(request.issueDate() == null ? LocalDate.now() : request.issueDate());
        charge.setDueDate(request.dueDate());
        charge.setStatus(request.dueDate().isBefore(LocalDate.now()) ? BillingCharge.Status.OVERDUE : BillingCharge.Status.PENDING);
        charge.setDigitableLine(clean(request.digitableLine()));
        charge.setBarcode(clean(request.barcode()));
        charge.setDocumentUrl(clean(request.documentUrl()));
        charge.setCreatedBy(actor(user));
        repository.save(charge);
        return toResponse(user.getTenantId(), charge);
    }

    public void sendByEmail(OidcUser oidcUser, UUID id, String requestedEmail) {
        AppUser user = requireManager(oidcUser);
        BillingCharge charge = requireCharge(user.getTenantId(), id);
        ApartmentOccupancy occupancy = requireOccupancy(user.getTenantId(), charge.getOccupancyId());
        String email = clean(requestedEmail);
        if (email == null) {
            List<String> emails = registryEntryRepository.findActiveResidentEmailsByUnit(
                    user.getTenantId(), RegistryEntry.EntryType.RESIDENT, occupancy.getBlock(), occupancy.getApartment());
            email = emails.stream().filter(e -> clean(e) != null).map(String::trim).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Nenhum e-mail ativo foi encontrado para esta unidade. Informe um e-mail para o envio."));
        }
        if (!email.contains("@")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "E-mail inválido.");

        String subject = "Boleto do condomínio - vencimento " + charge.getDueDate().format(DATE_BR);
        String plain = "Boleto referente à unidade Bloco " + occupancy.getBlock() + " Apto " + occupancy.getApartment()
                + ". Valor: R$ " + money(charge.getNominalValue())
                + ". Vencimento: " + charge.getDueDate().format(DATE_BR)
                + (clean(charge.getDigitableLine()) == null ? "" : ". Linha digitável: " + charge.getDigitableLine());
        String html = printableHtml(charge, occupancy, false);
        gmailService.send(user.getTenantId(), email, subject, plain, html, "Administração do condomínio");
    }

    public String printForAdministration(OidcUser oidcUser, UUID id) {
        AppUser user = requireManager(oidcUser);
        BillingCharge charge = requireCharge(user.getTenantId(), id);
        return printableHtml(charge, requireOccupancy(user.getTenantId(), charge.getOccupancyId()), true);
    }

    public String printForResident(HttpSession session, UUID id) {
        ResidentSessionService.ResidentContext ctx = residentSessionService.requirePortalContext(session);
        BillingCharge charge = repository.findByTenantIdAndIdAndDeletedFalse(ctx.tenant().getId(), id)
                .filter(c -> ctx.occupancy().getId().equals(c.getOccupancyId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boleto não encontrado."));
        return printableHtml(charge, ctx.occupancy(), true);
    }

    public void requestBankSync(OidcUser oidcUser) {
        AppUser user = requireManager(oidcUser);
        BankIntegrationConfig config = bankSettingsService.requireConfigured(user);
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "O módulo financeiro está pronto, mas o adaptador de produção do banco " + config.getProvider()
                        + " precisa ser habilitado com as credenciais/certificado fornecidos pelo banco.");
    }

    private String printableHtml(BillingCharge c, ApartmentOccupancy o, boolean autoPrint) {
        String line = clean(c.getDigitableLine());
        String url = clean(c.getDocumentUrl());
        String description = clean(c.getDescription());
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>Boleto</title><style>body{font-family:Arial,sans-serif;color:#17332f;margin:32px}.card{max-width:760px;margin:auto;border:1px solid #d8e6e2;border-radius:16px;padding:28px}h1{color:#0f766e}.row{margin:12px 0}.line{font-family:monospace;font-size:18px;word-break:break-all;background:#f3faf8;padding:14px;border-radius:10px}.btn{display:inline-block;margin-top:18px;padding:12px 18px;background:#0f766e;color:white;text-decoration:none;border-radius:10px}@media print{.btn{display:none}}</style></head><body>"
                + "<div class='card'><h1>Boleto do condomínio</h1>"
                + "<div class='row'><strong>Unidade:</strong> Bloco " + esc(o.getBlock()) + " · Apto " + esc(o.getApartment()) + "</div>"
                + "<div class='row'><strong>Descrição:</strong> " + esc(description == null ? "Cobrança condominial" : description) + "</div>"
                + "<div class='row'><strong>Valor:</strong> R$ " + money(c.getNominalValue()) + "</div>"
                + "<div class='row'><strong>Vencimento:</strong> " + c.getDueDate().format(DATE_BR) + "</div>"
                + (line == null ? "" : "<div class='row'><strong>Linha digitável:</strong><div class='line'>" + esc(line) + "</div></div>")
                + (url == null ? "" : "<a class='btn' href='" + esc(url) + "' target='_blank' rel='noopener'>Abrir boleto do banco</a>")
                + "</div>" + (autoPrint ? "<script>window.addEventListener('load',()=>setTimeout(()=>window.print(),250));</script>" : "") + "</body></html>";
    }

    private BillingCharge requireCharge(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Boleto não encontrado."));
    }
    private ApartmentOccupancy requireOccupancy(UUID tenantId, UUID id) {
        return occupancyRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidade/ocupação não encontrada."));
    }
    private BillingChargeResponse toResponse(UUID tenantId, BillingCharge c) {
        ApartmentOccupancy o = requireOccupancy(tenantId, c.getOccupancyId());
        BillingCharge.Status status = c.getStatus();
        if (status == BillingCharge.Status.PENDING && c.getDueDate().isBefore(LocalDate.now())) status = BillingCharge.Status.OVERDUE;
        return new BillingChargeResponse(c.getId(), c.getOccupancyId(), o.getBlock(), o.getApartment(), c.getBankProvider(), c.getExternalId(),
                c.getReferenceNumber(), c.getDescription(), c.getNominalValue(), c.getIssueDate(), c.getDueDate(), status,
                c.getDigitableLine(), c.getBarcode(), c.getDocumentUrl(), c.getLastBankUpdateAt());
    }
    private AppUser requireManager(OidcUser oidcUser) {
        AppUser user = authenticatedUserService.requireAppUser(oidcUser);
        accessControlService.requireSettingsManager(user);
        return user;
    }
    private String actor(AppUser user) { return clean(user.getEmail()) == null ? "system" : user.getEmail().trim(); }
    private String clean(String v) { return v == null || v.trim().isBlank() ? null : v.trim(); }
    private String money(BigDecimal v) { return String.format(Locale.forLanguageTag("pt-BR"), "%.2f", v == null ? BigDecimal.ZERO : v); }
    private String esc(String v) { return HtmlUtils.htmlEscape(v == null ? "" : v); }
}
