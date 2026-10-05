package com.packid.api.config;

import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // O header Origin enviado pelo navegador nunca contém path.
        // Ex.: para http://localhost:5173/packid/ o Origin é http://localhost:5173.
        // app.frontend-url continua podendo conter /packid/ porque também é usado
        // no redirect após o login OAuth.
        config.setAllowedOrigins(List.of(extractOrigin(frontendUrl)));

        // métodos permitidos (inclui OPTIONS pro preflight)
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // headers permitidos
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "X-Actor",
                "Accept"
        ));

        // importante: como o front usa credentials: "include"
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private String extractOrigin(String url) {
        URI uri = URI.create(url);
        if (uri.getScheme() == null || uri.getAuthority() == null) {
            throw new IllegalArgumentException("app.frontend-url deve ser uma URL absoluta: " + url);
        }
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}

