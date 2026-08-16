package com.packid.api.service;

import com.packid.api.controller.settings.dto.CondominiumSettingsResponse;
import com.packid.api.controller.settings.dto.CondominiumSettingsUpdateRequest;
import com.packid.api.controller.settings.dto.GoogleAccountSettingsResponse;
import com.packid.api.controller.settings.dto.PackIdLabelPrintSettingsResponse;
import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.Condominium;
import com.packid.api.domain.model.Tenant;
import com.packid.api.domain.model.TenantGoogleAccount;
import com.packid.api.domain.repository.CondominiumRepository;
import com.packid.api.domain.repository.TenantRepository;
import com.packid.api.integration.google.TenantGoogleAccountService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CondominiumSettingsService {
    private final AuthenticatedUserService authenticatedUserService;
    private final TenantRepository tenantRepository;
    private final CondominiumRepository condominiumRepository;
    private final TenantGoogleAccountService googleAccountService;

    public CondominiumSettingsService(
            AuthenticatedUserService authenticatedUserService,
            TenantRepository tenantRepository,
            CondominiumRepository condominiumRepository,
            TenantGoogleAccountService googleAccountService
    ) {
        this.authenticatedUserService = authenticatedUserService;
        this.tenantRepository = tenantRepository;
        this.condominiumRepository = condominiumRepository;
        this.googleAccountService = googleAccountService;
    }

    public CondominiumSettingsResponse get(OidcUser oidcUser) {
        AppUser appUser = requireAdmin(oidcUser);
        return responseFor(appUser);
    }

    @Transactional
    public CondominiumSettingsResponse update(OidcUser oidcUser, CondominiumSettingsUpdateRequest request) {
        AppUser appUser = requireAdmin(oidcUser);
        Tenant tenant = requireTenant(appUser);
        Condominium condominium = firstCondominium(appUser);

        String name = clean(request.name());
        if (name == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome do condomínio.");

        tenant.setName(name);
        tenant.setUpdatedBy(actor(appUser));
        tenantRepository.save(tenant);

        if (condominium == null) {
            condominium = new Condominium();
            condominium.setTenantId(appUser.getTenantId());
            condominium.setCreatedBy(actor(appUser));
        } else {
            condominium.setUpdatedBy(actor(appUser));
        }

        condominium.setName(name);
        condominium.setDocumentNumber(clean(request.documentNumber()));
        condominium.setAddressLine1(clean(request.addressLine1()));
        condominium.setAddressLine2(clean(request.addressLine2()));
        condominium.setCity(clean(request.city()));
        condominium.setState(clean(request.state()));
        condominium.setZipCode(clean(request.zipCode()));
        condominium.setPhone(clean(request.phone()));
        condominium.setEmail(clean(request.email()));
        condominium.setManagerName(clean(request.managerName()));
        condominium.setWhatsapp(clean(request.whatsapp()));
        condominium.setNotes(clean(request.notes()));
        condominium.setEmailNotificationsEnabled(!Boolean.FALSE.equals(request.emailNotificationsEnabled()));
        condominium.setPackIdPrintTwoLabels(!Boolean.FALSE.equals(request.packIdPrintTwoLabels()));
        condominiumRepository.save(condominium);

        return responseFor(appUser);
    }

    public AppUser requireAdmin(OidcUser oidcUser) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        if (!"ADMIN".equalsIgnoreCase(clean(appUser.getRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente administradores podem alterar as configurações do condomínio.");
        }
        return appUser;
    }

    public CondominiumSettingsResponse responseFor(AppUser appUser) {
        Tenant tenant = requireTenant(appUser);
        Condominium condominium = firstCondominium(appUser);
        TenantGoogleAccount googleAccount = googleAccountService.find(appUser.getTenantId()).orElse(null);
        return toResponse(tenant, condominium, googleAccount);
    }

    public PackIdLabelPrintSettingsResponse labelPrintSettings(OidcUser oidcUser) {
        AppUser appUser = authenticatedUserService.requireAppUser(oidcUser);
        Condominium condominium = firstCondominium(appUser);
        boolean printTwoLabels = condominium == null || !Boolean.FALSE.equals(condominium.getPackIdPrintTwoLabels());
        return new PackIdLabelPrintSettingsResponse(printTwoLabels ? 2 : 1);
    }

    private Tenant requireTenant(AppUser appUser) {
        return tenantRepository.findByIdAndDeletedFalse(appUser.getTenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio/tenant não encontrado."));
    }

    private Condominium firstCondominium(AppUser appUser) {
        List<Condominium> items = condominiumRepository.findAllByTenantIdAndDeletedFalse(appUser.getTenantId());
        return items.isEmpty() ? null : items.get(0);
    }

    private CondominiumSettingsResponse toResponse(Tenant tenant, Condominium c, TenantGoogleAccount g) {
        boolean connected = g != null && clean(g.getRefreshTokenEncrypted()) != null;
        GoogleAccountSettingsResponse google = g == null
                ? new GoogleAccountSettingsResponse(false, null, false, false, null, null, null)
                : new GoogleAccountSettingsResponse(
                        connected,
                        g.getEmail(),
                        connected && Boolean.TRUE.equals(g.getDriveEnabled()),
                        connected && Boolean.TRUE.equals(g.getGmailEnabled()),
                        g.getConnectedAt(),
                        g.getLastRefreshAt(),
                        g.getLastError()
                );

        return new CondominiumSettingsResponse(
                tenant.getId(), tenant.getSlug(), c == null ? null : c.getId(),
                c == null ? tenant.getName() : c.getName(),
                c == null ? null : c.getDocumentNumber(),
                c == null ? null : c.getAddressLine1(),
                c == null ? null : c.getAddressLine2(),
                c == null ? null : c.getCity(),
                c == null ? null : c.getState(),
                c == null ? null : c.getZipCode(),
                c == null ? null : c.getPhone(),
                c == null ? null : c.getEmail(),
                c == null ? null : c.getManagerName(),
                c == null ? null : c.getWhatsapp(),
                c == null ? null : c.getNotes(),
                c == null || !Boolean.FALSE.equals(c.getEmailNotificationsEnabled()),
                c == null || !Boolean.FALSE.equals(c.getPackIdPrintTwoLabels()),
                google
        );
    }

    private String actor(AppUser appUser) {
        String email = clean(appUser.getEmail());
        return email == null ? "system" : email;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }
}
