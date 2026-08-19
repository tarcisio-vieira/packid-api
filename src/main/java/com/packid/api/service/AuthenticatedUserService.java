package com.packid.api.service;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.repository.AppUserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthenticatedUserService {

    private final AppUserRepository appUserRepository;

    public AuthenticatedUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public AppUser requireAppUser(OidcUser oidcUser) {
        if (oidcUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado");
        }

        String subject = trimToNull(oidcUser.getSubject());
        if (subject != null) {
            List<AppUser> bySubject = appUserRepository
                    .findAllByProviderAndProviderSubjectAndDeletedFalse(AppUser.AuthProvider.GOOGLE, subject);

            if (bySubject.size() == 1) {
                return validateEnabled(bySubject.get(0));
            }
            if (bySubject.size() > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Conta Google associada a mais de um usuário PackID.");
            }
        }

        String email = trimToNull(oidcUser.getEmail());
        if (email != null) {
            List<AppUser> byEmail = appUserRepository.findAllByEmailAndDeletedFalse(email);
            if (byEmail.size() == 1) {
                AppUser appUser = validateEnabled(byEmail.get(0));
                // Permite pré-cadastrar o e-mail (ex.: portaria) sem conhecer o subject do Google.
                // No primeiro login OAuth2 o vínculo definitivo é gravado automaticamente.
                if (subject != null && trimToNull(appUser.getProviderSubject()) == null) {
                    appUser.setProvider(AppUser.AuthProvider.GOOGLE);
                    appUser.setProviderSubject(subject);
                    appUser.setUpdatedBy(email);
                    // Persistir apenas o vínculo inicial com a conta Google.
                    // Consultas normais não devem gerar UPDATE em app_user.
                    appUser = appUserRepository.save(appUser);
                }
                return appUser;
            }
            if (byEmail.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Usuário não cadastrado no VSGI Condomínio: " + email);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "E-mail associado a mais de um usuário VSGI Condomínio.");
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Não foi possível identificar o usuário autenticado.");
    }

    /**
     * Resolve o usuário autenticado e registra o instante de login/acesso inicial.
     * Deve ser usado apenas no endpoint de sessão (/api/app-users/me),
     * nunca em endpoints de polling/consulta periódica.
     */
    @Transactional
    public AppUser requireAppUserAndTouchLogin(OidcUser oidcUser) {
        return touchLogin(requireAppUser(oidcUser));
    }

    private AppUser touchLogin(AppUser user) {
        user.setLastLoginAt(LocalDateTime.now());
        return appUserRepository.save(user);
    }

    private AppUser validateEnabled(AppUser user) {
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário desabilitado.");
        }
        return user;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
