package com.fsc.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Configuracao de segurança do Gateway FSC.
 *
 * Politica: Zero-Trust por padrao.
 * - Todas as rotas exigem JWT Bearer valido do Keycloak (OAuth2 Resource Server).
 * - Excecoes explicitas: health check do Actuator, Swagger UI e Prometheus (apenas em rede interna).
 * - Headers de segurança HTTP habilitados (HSTS, CSP, X-Frame-Options, Referrer-Policy).
 * - Sessao completamente stateless (JWT-only).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Endpoints publicos permitidos explicitamente.
     * Swagger UI exposto apenas para facilitar integracao; em producao considere restringir por IP.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health",
        "/actuator/prometheus",
        "/actuator/info",
        // OpenAPI / Swagger UI (SpringDoc)
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Stateless: sem sessao HTTP
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF desnecessario em APIs stateless com JWT
            .csrf(csrf -> csrf.disable())

            // Autorizacao de rotas — Zero-Trust: tudo autenticado exceto excecoes explicitas
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .anyRequest().authenticated()   // CORRECAO: nenhuma rota anonima
            )

            // JWT Bearer via Keycloak JWKS
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))

            // Headers de segurança HTTP
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives("default-src 'self'; frame-ancestors 'none'"))
                .referrerPolicy(referrer ->
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .permissionsPolicy(pp ->
                    pp.policy("camera=(), microphone=(), geolocation=()"))
            );

        return http.build();
    }
}
