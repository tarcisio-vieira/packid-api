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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResidentSessionService {
    private static final String SESSION_TENANT_ID = "resident.tenantId";
    private static final String SESSION_OCCUPANCY_ID = "resident.occupancyId";
    private static final String SESSION_RESIDENT_ENTRY_ID = "resident.registryEntryId";

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
        String username = cleanRequired(request.username()).toLowerCase(Locale.ROOT);
        String password = cleanRequired(request.password());

        List<RegistryEntry> candidates = registryEntryRepository
                .findAllByEntryTypeAndResidentUsernameIgnoreCaseAndActiveTrueAndDeletedFalse(
                        RegistryEntry.EntryType.RESIDENT, username);

        List<ResidentContext> matches = new ArrayList<>();
        for (RegistryEntry resident : candidates) {
            if (resident.getOccupancyId() == null || clean(resident.getResidentPasswordHash()) == null) continue;
            if (!passwordEncoder.matches(password, resident.getResidentPasswordHash())) continue;

            Tenant tenant = tenantRepository.findByIdAndDeletedFalse(resident.getTenantId())
                    .filter(item -> Boolean.TRUE.equals(item.getActive()))
                    .orElse(null);
            if (tenant == null) continue;

            ApartmentOccupancy occupancy = occupancyRepository
                    .findByTenantIdAndIdAndDeletedFalse(tenant.getId(), resident.getOccupancyId())
                    .filter(item -> item.getStatus() == ApartmentOccupancy.Status.ACTIVE)
                    .orElse(null);
            if (occupancy == null) continue;

            matches.add(new ResidentContext(tenant, occupancy, resident));
        }

        // Usuário + senha precisam identificar uma única pessoa, independentemente do tenant.
        if (matches.size() != 1) throw invalidLogin();

        ResidentContext context = matches.get(0);
        session.setAttribute(SESSION_TENANT_ID, context.tenant().getId().toString());
        session.setAttribute(SESSION_OCCUPANCY_ID, context.occupancy().getId().toString());
        session.setAttribute(SESSION_RESIDENT_ENTRY_ID, context.resident().getId().toString());
        session.setMaxInactiveInterval(12 * 60 * 60);
        return toResponse(context.tenant(), context.occupancy(), context.resident());
    }

    public ResidentContext requireContext(HttpSession session) {
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão do morador não encontrada.");
        }

        UUID tenantId = parseUuid(session.getAttribute(SESSION_TENANT_ID));
        UUID occupancyId = parseUuid(session.getAttribute(SESSION_OCCUPANCY_ID));
        UUID residentEntryId = parseUuid(session.getAttribute(SESSION_RESIDENT_ENTRY_ID));
        if (tenantId == null || occupancyId == null || residentEntryId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Faça login como morador para continuar.");
        }

        Tenant tenant = tenantRepository.findByIdAndDeletedFalse(tenantId)
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Condomínio indisponível."));

        ApartmentOccupancy occupancy = occupancyRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, occupancyId)
                .filter(item -> item.getStatus() == ApartmentOccupancy.Status.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A ocupação desta unidade não está mais ativa."));

        RegistryEntry resident = registryEntryRepository.findByTenantIdAndIdAndDeletedFalse(tenantId, residentEntryId)
                .filter(item -> item.getEntryType() == RegistryEntry.EntryType.RESIDENT)
                .filter(item -> Boolean.TRUE.equals(item.getActive()))
                .filter(item -> occupancyId.equals(item.getOccupancyId()))
                .filter(item -> clean(item.getResidentUsername()) != null && clean(item.getResidentPasswordHash()) != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "O acesso deste morador não está mais ativo."));

        return new ResidentContext(tenant, occupancy, resident);
    }

    public ResidentSessionResponse current(HttpSession session) {
        ResidentContext context = requireContext(session);
        return toResponse(context.tenant(), context.occupancy(), context.resident());
    }

    public ResidentContext requirePortalContext(HttpSession session) {
        ResidentContext context = requireContext(session);
        if (Boolean.TRUE.equals(context.resident().getResidentMustChangePassword())) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED,
                    "Altere a senha temporária antes de acessar os dados da unidade.");
        }
        return context;
    }

    @Transactional
    public ResidentSessionResponse updateCredentials(HttpSession session, ResidentCredentialsUpdateRequest request) {
        ResidentContext context = requireContext(session);
        RegistryEntry resident = context.resident();

        String username = clean(request.username());
        if (username != null) {
            if (username.length() < 4 || username.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "O usuário deve ter entre 4 e 100 caracteres.");
            }
            username = username.toLowerCase(Locale.ROOT);
            RegistryEntry conflict = registryEntryRepository
                    .findByTenantIdAndResidentUsernameIgnoreCaseAndDeletedFalse(context.tenant().getId(), username)
                    .orElse(null);
            if (conflict != null && !conflict.getId().equals(resident.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Este nome de usuário já está em uso neste condomínio.");
            }
            resident.setResidentUsername(username);
        }

        String password = request.newPassword();
        if (password != null && !password.isBlank()) {
            validatePassword(password);
            resident.setResidentPasswordHash(passwordEncoder.encode(password));
            resident.setResidentMustChangePassword(false);
        } else if (Boolean.TRUE.equals(resident.getResidentMustChangePassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No primeiro acesso é obrigatório definir uma nova senha.");
        }

        resident.setUpdatedBy("morador:" + resident.getResidentUsername());
        registryEntryRepository.save(resident);
        return toResponse(context.tenant(), context.occupancy(), resident);
    }

    public void logout(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(SESSION_TENANT_ID);
        session.removeAttribute(SESSION_OCCUPANCY_ID);
        session.removeAttribute(SESSION_RESIDENT_ENTRY_ID);
    }

    private ResidentSessionResponse toResponse(Tenant tenant, ApartmentOccupancy occupancy, RegistryEntry resident) {
        return new ResidentSessionResponse(
                occupancy.getId(),
                resident.getId(),
                resident.getName(),
                tenant.getName(),
                tenant.getSlug(),
                occupancy.getBlock(),
                occupancy.getApartment(),
                resident.getResidentUsername(),
                Boolean.TRUE.equals(resident.getResidentMustChangePassword())
        );
    }

    private void validatePassword(String password) {
        if (password.length() < 8
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A senha deve ter pelo menos 8 caracteres, com letra maiúscula, minúscula e número.");
        }
    }

    private ResponseStatusException invalidLogin() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário ou senha inválidos.");
    }

    private UUID parseUuid(Object value) {
        if (value == null) return null;
        try { return UUID.fromString(String.valueOf(value)); }
        catch (IllegalArgumentException ignored) { return null; }
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
