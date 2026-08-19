package com.packid.api.service;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.model.RegistryEntry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AccessControlService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SECRETARY = "SECRETARY";
    public static final String ROLE_PORTER = "PORTER";
    public static final String ROLE_POOL_ATTENDANT = "POOL_ATTENDANT";

    public boolean isAdmin(AppUser user) { return ROLE_ADMIN.equals(role(user)); }
    public boolean isSecretary(AppUser user) { return ROLE_SECRETARY.equals(role(user)); }
    public boolean isPorter(AppUser user) { return ROLE_PORTER.equals(role(user)); }
    public boolean isPoolAttendant(AppUser user) { return ROLE_POOL_ATTENDANT.equals(role(user)); }

    public boolean canManageSettings(AppUser user) { return isAdmin(user) || isSecretary(user); }
    public boolean canManageProtectedRegistry(AppUser user) { return isAdmin(user) || isSecretary(user); }
    public boolean canOperateCondominium(AppUser user) { return isAdmin(user) || isSecretary(user) || isPorter(user); }
    public boolean canViewPoolCards(AppUser user) {
        return isAdmin(user) || isSecretary(user) || isPorter(user) || isPoolAttendant(user);
    }
    public boolean canManagePoolCards(AppUser user) { return isAdmin(user) || isSecretary(user); }

    public void requireAdmin(AppUser user) {
        if (!isAdmin(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Somente o administrador técnico pode executar esta ação.");
    }

    public void requireSettingsManager(AppUser user) {
        if (!canManageSettings(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Somente administrador ou secretaria podem acessar as configurações do condomínio.");
    }

    public void requireProtectedRegistryManager(AppUser user) {
        if (!canManageProtectedRegistry(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "O perfil de portaria possui somente visualização para condôminos, bicicletas, pets e veículos.");
    }

    public void requireOperationalUser(AppUser user) {
        if (!canOperateCondominium(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Usuário sem permissão operacional no condomínio.");
    }

    public void requirePoolCardViewer(AppUser user) {
        if (!canViewPoolCards(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Usuário sem permissão para consultar carteirinhas de piscina.");
    }

    public void requirePoolCardManager(AppUser user) {
        if (!canManagePoolCards(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Somente administrador ou secretaria podem incluir, editar ou excluir carteirinhas de piscina.");
    }

    public void requireAdministrativeUser(AppUser user) {
        if (!isAdmin(user) && !isSecretary(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Somente administrador ou secretaria podem exportar os dados do condomínio.");
    }

    public void requireMutationPermission(AppUser user, RegistryEntry.EntryType type) {
        if (isProtectedRegistryType(type)) requireProtectedRegistryManager(user);
        else requireOperationalUser(user);
    }

    public boolean isProtectedRegistryType(RegistryEntry.EntryType type) {
        return type == RegistryEntry.EntryType.RESIDENT
                || type == RegistryEntry.EntryType.BICYCLE
                || type == RegistryEntry.EntryType.PET
                || type == RegistryEntry.EntryType.VEHICLE;
    }

    public String normalizeRole(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) return null;
        return switch (normalized) {
            case ROLE_ADMIN, ROLE_SECRETARY, ROLE_PORTER, ROLE_POOL_ATTENDANT -> normalized;
            case "PISCINEIRO", "POOL", "POOL_ATTENDANT_USER" -> ROLE_POOL_ATTENDANT;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Perfil inválido. Use ADMIN, SECRETARY, PORTER ou POOL_ATTENDANT.");
        };
    }

    private String role(AppUser user) {
        if (user == null || user.getRole() == null) return "";
        return user.getRole().trim().toUpperCase(Locale.ROOT);
    }
}
