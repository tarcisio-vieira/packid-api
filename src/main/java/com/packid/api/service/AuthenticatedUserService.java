package com.packid.api.service;

import com.packid.api.domain.model.AppUser;
import com.packid.api.domain.repository.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AuthenticatedUserService {

    private final AppUserRepository appUserRepository;

    public AuthenticatedUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

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
                return validateEnabled(byEmail.get(0));
            }
            if (byEmail.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Usuário não cadastrado no PackID: " + email);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "E-mail associado a mais de um usuário PackID.");
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Não foi possível identificar o usuário autenticado.");
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
