package com.packid.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // endpoints públicos
                .requestMatchers("/", "/public/**", "/health").permitAll()
                // tudo o resto precisa estar logado
                .anyRequest().authenticated()
            )
            // login via Google OAuth2
            .oauth2Login(Customizer.withDefaults())
            // opcional: logout padrão
            .logout(logout -> logout
                .logoutSuccessUrl("/").permitAll()
            );

        return http.build();
    }
}
