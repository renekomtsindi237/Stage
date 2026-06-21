package cm.imf.pipeline.controller;

import cm.imf.pipeline.filter.InternalApiKeyFilter;
import cm.imf.pipeline.security.JwtAuthenticationFilter;
import cm.imf.pipeline.security.JwtTokenProvider;
import cm.imf.pipeline.security.UserDetailsServiceImpl;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Configuration de sécurité simplifiée pour les tests @WebMvcTest.
 *
 * Problème résolu : JwtAuthenticationFilter est un @Component, donc Spring Boot
 * l'enregistre automatiquement comme Servlet filter. Le mock Mockito ne fait rien
 * dans doFilterInternal() → filterChain.doFilter() n'est jamais appelé → la chaîne
 * s'arrête → toutes les réponses sont 200 vides.
 *
 * Solution : FilterRegistrationBean.setEnabled(false) — désactive l'enregistrement
 * du filtre mock comme Servlet filter standalone. Le filtre n'est donc jamais appelé.
 * L'authentification est injectée via @WithMockUser / TestHelper.asXxx().
 */
@TestConfiguration
@EnableMethodSecurity
public class TestSecurityConfig {

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean StringRedisTemplate stringRedisTemplate;
        @MockBean InternalApiKeyFilter internalApiKeyFilter;

    /**
     * Désactive l'auto-enregistrement du filtre JWT comme Servlet filter standalone.
     * Sans ça, Spring Boot enregistre le mock dans la chaîne Servlet,
     * il n'appelle pas filterChain.doFilter(), et tous les tests reçoivent 200 vide.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilterRegistration(
            InternalApiKeyFilter filter) {
        FilterRegistrationBean<InternalApiKeyFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/api/v1/ping", "/api/v1/health", "/api/v1/internal/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/alertes/**")
                        .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DSI")
                        .requestMatchers(HttpMethod.POST, "/api/v1/clients/import")
                                .hasAnyRole("AGENT_CREDIT", "CHEF_AGENCE", "DSI", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/collectes").hasRole("AGENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/collectes/mes-collectes").hasRole("AGENT")
                        .requestMatchers("/api/v1/kyc/**").hasAnyRole("DSI", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/recouvrement/**")
                        .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/rgpd/**").hasAnyRole("DSI", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/admin/violations/**").hasAnyRole("DSI", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/prets/mes-prets").hasRole("AGENT")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(h -> h.cacheControl(c -> c.disable()))
                .build();
    }
}
