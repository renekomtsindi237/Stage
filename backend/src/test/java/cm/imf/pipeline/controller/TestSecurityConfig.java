package cm.imf.pipeline.controller;

import cm.imf.pipeline.security.JwtAuthenticationFilter;
import cm.imf.pipeline.security.JwtTokenProvider;
import cm.imf.pipeline.security.UserDetailsServiceImpl;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Configuration de sécurité simplifiée pour les tests @WebMvcTest.
 * - Pas de JwtAuthenticationFilter dans la chaîne (le mock ne ferait rien)
 * - @WithMockUser / TestHelper.asXxx() injectent l'authentification via SecurityContext
 * - @EnableMethodSecurity active @PreAuthorize
 * - /ping et /health sont publics (ConnectivityController)
 * - Cache-Control Spring Security désactivé pour laisser le controller définir le sien
 */
@TestConfiguration
@EnableMethodSecurity
public class TestSecurityConfig {

    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean StringRedisTemplate stringRedisTemplate;

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**", "/ping", "/health").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .headers(h -> h.cacheControl(c -> c.disable()))
                .build();
    }
}
