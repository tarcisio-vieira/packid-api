package com.packid.api.service;

import com.packid.api.controller.resident.dto.ResidentCredentialsUpdateRequest;
import com.packid.api.controller.resident.dto.ResidentLoginRequest;
import com.packid.api.controller.resident.dto.ResidentSessionResponse;
import com.packid.api.domain.model.ApartmentOccupancy;
import com.packid.api.domain.model.RegistryEntry;
import com.packid.api.domain.model.Tenant;
import com.packid.api.domain.repository.ApartmentOccupancyRepository;
import com.packid.api.domain.repository.RegistryEntryRepository;
import com.packid.api.domain.repository.TenantRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResidentSessionService {
    private static final String SESSION_TENANT_ID = "resident.tenantId";
    private static final String SESSION_OCCUPANCY_ID = "resident.occupancyId";

    private final TenantRepository tenantRepository;
    private final ApartmentOccupancyRepository occupancyRepository;
    private final RegistryEntryRepository registryEntryRepository;
    private final PasswordEncoder passwordEncoder;

    public ResidentSessionService(
            TenantRepository tenantRepository,
            ApartmentOccupancyRepository occupancyRepository,
            RegistryEntryRepository registryEntryRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.occupancyRepository = occupancyRepository;
        this.registryEntryRepository = registryEntryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public ResidentSessionResponse login(ResidentLoginRequest request, HttpSession session) {
        String tenantSlug = cleanRequired(request.tenantSlug());
        String username = cleanRequired(request.username()).toLowerCase(Locale.ROOT);
        String block = cleanRequired(request.block());
        String apartment = cleanRequired(request.apartment());

        Tenant tenant = tenantRepository.findBySlugAndActiveTrue(tenantSlug)
                .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                .orElseThrow(this::invalidLogin);

        ApartmentOccupancy occupancy = occupancyRepository
                .findByTenantIdAndResidentUsernameIgnoreCaseAndResidentAccessEnabledTrueAndStatusAndDeletedFalse(
                        tenant.getId(), username, ApartmentOccupancy.Status.ACTIVE)
                .orElseThrow(this::invalidLogin);

        if (!same(occupancy.getBlock(), block)
                || !same(occupancy.getApartment(), apartment)
                || clean(occupancy.getResidentPasswordHash()) == null
                || !passwordEncoder.matches(request.password(), occupancy.getResidentPasswordHash())) {
            throw invalidLogin();
        }

        session.setAttribute(SESSION_TENANT_ID, tenant.getId().toString());
        session.setAttribute(SESSION_OCCUPANCY_ID, occupancy.getId().toString());
        session.setMaxInactiveInterval(12 * 60 * 60);
        return toResponse(tenant, occupancy);
    }

    public ResidentContext requireContext(HttpSession session) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão do morador não encontrada.");
        }

        UUID tenantId = parseUuid(session.getAttribute(SESSION_TENANT_ID));
        UUID occupancyId = parseUuid(session.getAttribute(SESSION_OCCUPANCY_ID));
        if (tenantId == null || occupancyId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login como morador para continuar.");
        }

        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(tenantId)
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Condomínio indisponível."));

        ApartmentOccupancy occupancy = occupancyRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, occupancyId)
                .filter(item -> item.getStatus() == ApartmentOccupancy.Status.ACTIVE)
                .filter(item -> Boolean.TRUE.equals(item.getResidentAccessEnabled()))
                .filter(item -> clean(item.getResidentUsername()) != null && clean(item.getResidentPasswordHash()) != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "O acesso desta unidade não está mais ativo."));

        List<RegistryEntry> residents = registryEntryRepository
                .findAllByTenantIdAndOccupancyIdAndEntryTypeAndActiveTrueAndDeletedFalseOrderByNameAsc(
                        tenantId, occupancyId, RegistryEntry.EntryType.RESIDENT);
        RegistryEntry representative = residents.stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "A ocupação não possui condômino ativo para registrar as solicitações da unidade."));

        return new ResidentContext(tenant, occupancy, representative);
    }

    public ResidentSessionResponse current(HttpSession session) {
        ResidentContext context = requireContext(session);
        return toResponse(context.tenant(), context.occupancy());
    }

    public ResidentContext requirePortalContext(HttpSession session) {
        ResidentContext context = requireContext(session);
        if (Boolean.TRUE.equals(context.occupancy().getResidentMustChangePassword())) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Altere a senha temporária antes de acessar os dados da unidade.");
        }
        return context;
    }

    @Transactional
    public ResidentSessionResponse updateCredentials(HttpSession session, ResidentCredentialsUpdateRequest request) {
        ResidentContext context = requireContext(session);
        ApartmentOccupancy occupancy = context.occupancy();

        String username = clean(request.username());
        if (username != null) {
            if (username.length() < 4 || username.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O usuário deve ter entre 4 e 100 caracteres.");
            }
            username = username.toLowerCase(Locale.ROOT);
            ApartmentOccupancy conflict = occupancyRepository
                    .findByTenantIdAndResidentUsernameIgnoreCaseAndDeletedFalse(context.tenant().getId(), username)
                    .orElse(null);
            if (conflict != null && !conflict.getId().equals(occupancy.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Este nome de usuário já está em uso no condomínio.");
            }
            occupancy.setResidentUsername(username);
        }

        String password = request.newPassword();
        if (password != null && !password.isBlank()) {
            if (password.length() < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A nova senha deve ter pelo menos 8 caracteres.");
            }
            occupancy.setResidentPasswordHash(passwordEncoder.encode(password));
            occupancy.setResidentMustChangePassword(false);
        } else if (Boolean.TRUE.equals(occupancy.getResidentMustChangePassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No primeiro acesso é obrigatório definir uma nova senha.");
        }

        occupancy.setUpdatedBy("morador:" + occupancy.getResidentUsername());
        occupancyRepository.save(occupancy);
        return toResponse(context.tenant(), occupancy);
    }

    public void logout(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(SESSION_TENANT_ID);
        session.removeAttribute(SESSION_OCCUPANCY_ID);
    }

    private ResidentSessionResponse toResponse(Tenant tenant, ApartmentOccupancy occupancy) {
        return new ResidentSessionResponse(
                occupancy.getId(),
                tenant.getName(),
                tenant.getSlug(),
                occupancy.getBlock(),
                occupancy.getApartment(),
                occupancy.getResidentUsername(),
                Boolean.TRUE.equals(occupancy.getResidentMustChangePassword())
        );
    }

    private ResponseStatusException invalidLogin() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Usuário, senha, bloco, apartamento ou condomínio inválido.");
    }

    private UUID parseUuid(Object value) {
        if (value == null) return null;
        try { return UUID.fromString(String.valueOf(value)); }
        catch (IllegalArgumentException ignored) { return null; }
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

    public record ResidentContext(Tenant tenant, ApartmentOccupancy occupancy, RegistryEntry resident) {}
}
