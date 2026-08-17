package com.packid.api.service;

import com.packid.api.controller.resident.dto.ResidentLoginRequest;
import com.packid.api.controller.resident.dto.ResidentSessionResponse;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.Tenant;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.repository.TenantRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ResidentSessionService {
    private static final String SESSION_TENANT_ID = "resident.tenantId";
    private static final String SESSION_RESIDENT_ID = "resident.registryEntryId";

    private final TenantRepository tenantRepository;
    private final RegistryEntryRepository registryEntryRepository;
    private final PasswordEncoder passwordEncoder;

    public ResidentSessionService(
            TenantRepository tenantRepository,
            RegistryEntryRepository registryEntryRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.registryEntryRepository = registryEntryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public ResidentSessionResponse login(ResidentLoginRequest request, HttpSession session) {
        String tenantSlug = cleanRequired(request.tenantSlug());
        String username = cleanRequired(request.username()).toLowerCase();
        String block = cleanRequired(request.block());
        String apartment = cleanRequired(request.apartment());

        Tenant tenant = tenantRepository.findBySlugAndActiveTrue(tenantSlug)
                .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                .orElseThrow(this::invalidLogin);

        RegistryEntry resident = registryEntryRepository
                .findByTenantIdAndEntryTypeAndResidentUsernameIgnoreCaseAndActiveTrueAndDeletedFalse(
                        tenant.getId(), RegistryEntry.EntryType.RESIDENT, username)
                .orElseThrow(this::invalidLogin);

        if (!same(resident.getBlock(), block)
                || !same(resident.getApartment(), apartment)
                || clean(resident.getResidentPasswordHash()) == null
                || !passwordEncoder.matches(request.password(), resident.getResidentPasswordHash())) {
            throw invalidLogin();
        }

        session.setAttribute(SESSION_TENANT_ID, tenant.getId().toString());
        session.setAttribute(SESSION_RESIDENT_ID, resident.getId().toString());
        session.setMaxInactiveInterval(12 * 60 * 60);
        return toResponse(tenant, resident);
    }

    public ResidentContext requireContext(HttpSession session) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão do morador não encontrada.");
        }

        UUID tenantId = parseUuid(session.getAttribute(SESSION_TENANT_ID));
        UUID residentId = parseUuid(session.getAttribute(SESSION_RESIDENT_ID));
        if (tenantId == null || residentId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login como morador para continuar.");
        }

        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(tenantId)
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Condomínio indisponível."));

        RegistryEntry resident = registryEntryRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, residentId)
                .filter(item -> item.getEntryType() == RegistryEntry.EntryType.RESIDENT)
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .filter(item -> clean(item.getResidentUsername()) != null && clean(item.getResidentPasswordHash()) != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "O acesso deste morador não está mais ativo."));

        return new ResidentContext(tenant, resident);
    }

    public ResidentSessionResponse current(HttpSession session) {
        ResidentContext context = requireContext(session);
        return toResponse(context.tenant(), context.resident());
    }

    public void logout(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(SESSION_TENANT_ID);
        session.removeAttribute(SESSION_RESIDENT_ID);
    }

    private ResidentSessionResponse toResponse(Tenant tenant, RegistryEntry resident) {
        return new ResidentSessionResponse(
                resident.getId(),
                resident.getName(),
                tenant.getName(),
                tenant.getSlug(),
                resident.getBlock(),
                resident.getApartment()
        );
    }

    private ResponseStatusException invalidLogin() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Usuário, senha, bloco, apartamento ou condomínio inválido.");
    }

    private UUID parseUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean same(String left, String right) {
        String a = clean(left), b = clean(right);
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String cleanRequired(String value) {
        String cleaned = clean(value);
        if (cleaned == null) throw invalidLogin();
        return cleaned;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    public record ResidentContext(Tenant tenant, RegistryEntry resident) {}
}
