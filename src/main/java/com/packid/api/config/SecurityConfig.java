package com.packid.api.config;

import com.packid.api.controller.settings.CondominiumSettingsController;
import com.packid.api.integration.google.TenantGoogleAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Configuration
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private static final String GOOGLE_REGISTRATION_ID = "google";

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    OAuth2AuthorizationRequestResolver googleAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        DefaultOAuth2AuthorizationRequestResolver delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, "/oauth2/authorization");

        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                return customize(delegate.resolve(request), request);
            }

            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
                return customize(delegate.resolve(request, clientRegistrationId), request);
            }

            private OAuth2AuthorizationRequest customize(
                    OAuth2AuthorizationRequest authorizationRequest,
                    HttpServletRequest request
            ) {
                if (authorizationRequest == null) return null;

                Map<String, Object> additional = new LinkedHashMap<>(authorizationRequest.getAdditionalParameters());
                additional.put("access_type", "offline");
                additional.put("include_granted_scopes", "true");

                HttpSession session = request.getSession(false);
                boolean forceConsent = session != null
                        && Boolean.TRUE.equals(session.getAttribute(CondominiumSettingsController.FORCE_GOOGLE_CONSENT));

                OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.from(authorizationRequest)
                        .additionalParameters(additional);

                if (forceConsent) {
                    // Faz o Google mostrar a escolha de conta. Assim o administrador pode manter
                    // a própria sessão no VSGI e conectar uma conta institucional diferente.
                    additional.put("prompt", "consent select_account");
                    Set<String> scopes = new LinkedHashSet<>(authorizationRequest.getScopes());
                    scopes.add(GMAIL_SEND_SCOPE);
                    builder.additionalParameters(additional).scopes(scopes);
                    session.removeAttribute(CondominiumSettingsController.FORCE_GOOGLE_CONSENT);
                }

                return builder.build();
            }
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            OAuth2AuthorizationRequestResolver googleAuthorizationRequestResolver,
            OAuth2AuthorizedClientService authorizedClientService,
            TenantGoogleAccountService googleAccountService
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**",
                                "/manifest.webmanifest", "/robots.txt").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**", "/error").permitAll()
                        .requestMatchers("/actuator/health", "/health", "/public/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getServletPath().startsWith("/api/")))
                .oauth2Client(Customizer.withDefaults())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(googleAuthorizationRequestResolver))
                        .successHandler((request, response, authentication) -> {
                            HttpSession session = request.getSession(false);
                            boolean returnToSettings = session != null
                                    && Boolean.TRUE.equals(session.getAttribute(CondominiumSettingsController.RETURN_TO_SETTINGS));

                            if (!returnToSettings || session == null) {
                                redirect(response, false, null);
                                return;
                            }

                            boolean connected = false;
                            try {
                                if (!(authentication.getPrincipal() instanceof OidcUser officialUser)) {
                                    throw new IllegalStateException("O Google não retornou os dados da conta autenticada.");
                                }

                                String tenantIdText = sessionString(session, CondominiumSettingsController.PENDING_TENANT_ID);
                                String actor = sessionString(session, CondominiumSettingsController.PENDING_ACTOR);
                                if (tenantIdText == null) {
                                    throw new IllegalStateException("O tenant do condomínio não foi identificado.");
                                }

                                OAuth2AuthorizedClient officialClient = authorizedClientService.loadAuthorizedClient(
                                        GOOGLE_REGISTRATION_ID, authentication.getName());
                                if (officialClient == null) {
                                    throw new IllegalStateException("A autorização Google não ficou disponível para o VSGI.");
                                }

                                googleAccountService.connectForTenant(
                                        UUID.fromString(tenantIdText),
                                        actor,
                                        officialUser.getEmail(),
                                        officialUser.getSubject(),
                                        officialClient
                                );
                                connected = true;
                            } catch (Exception ex) {
                                log.warn("Não foi possível conectar a conta Google oficial do condomínio: {}", ex.getMessage());
                            } finally {
                                restorePreviousAuthentication(session, request, response);
                                clearGoogleConnectionSession(session);
                            }

                            redirect(response, true, connected ? "googleConnected=1" : "googleError=1");
                        })
                        .failureHandler((request, response, exception) -> {
                            HttpSession session = request.getSession(false);
                            boolean returnToSettings = session != null
                                    && Boolean.TRUE.equals(session.getAttribute(CondominiumSettingsController.RETURN_TO_SETTINGS));

                            if (returnToSettings && session != null) {
                                log.warn("Autorização da conta Google oficial cancelada ou recusada: {}", exception.getMessage());
                                restorePreviousAuthentication(session, request, response);
                                clearGoogleConnectionSession(session);
                                redirect(response, true, "googleError=1");
                                return;
                            }

                            redirect(response, false, "loginError=1");
                        }))
                .logout(logout -> logout.logoutSuccessUrl(frontendUrl).permitAll());

        return http.build();
    }

    private void restorePreviousAuthentication(
            HttpSession session,
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response
    ) {
        Object previous = session.getAttribute(CondominiumSettingsController.PREVIOUS_AUTHENTICATION);
        if (!(previous instanceof Authentication previousAuthentication)) return;

        SecurityContext restoredContext = SecurityContextHolder.createEmptyContext();
        restoredContext.setAuthentication(previousAuthentication);
        SecurityContextHolder.setContext(restoredContext);

        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.saveContext(restoredContext, request, response);
    }

    private void clearGoogleConnectionSession(HttpSession session) {
        session.removeAttribute(CondominiumSettingsController.PREVIOUS_AUTHENTICATION);
        session.removeAttribute(CondominiumSettingsController.PENDING_TENANT_ID);
        session.removeAttribute(CondominiumSettingsController.PENDING_ACTOR);
        session.removeAttribute(CondominiumSettingsController.FORCE_GOOGLE_CONSENT);
        session.removeAttribute(CondominiumSettingsController.RETURN_TO_SETTINGS);
    }

    private String sessionString(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private void redirect(
            jakarta.servlet.http.HttpServletResponse response,
            boolean settings,
            String query
    ) throws IOException {
        if (!settings) {
            response.sendRedirect(appendQuery(frontendUrl, query));
            return;
        }

        String target = appendQuery(frontendUrl, "view=settings");
        response.sendRedirect(appendQuery(target, query));
    }

    private String appendQuery(String url, String query) {
        if (query == null || query.isBlank()) return url;
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + query;
    }
}
