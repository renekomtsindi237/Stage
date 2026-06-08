package cm.imf.pipeline.config;

import cm.imf.pipeline.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuration de la sécurité Spring.
 *
 * L'API est entièrement stateless : pas de session HTTP, chaque requête
 * doit porter un token JWT valide dans le header Authorization.
 *
 * Le filtre JwtAuthenticationFilter est exécuté avant UsernamePasswordAuthenticationFilter
 * pour valider le token et peupler le SecurityContext avant que Spring ne
 * vérifie les droits d'accès.
 *
 * CORS : en développement on autorise localhost:* pour le client Angular.
 * En production, seuls les sous-domaines *.imf.cm sont autorisés.
 *
 * BCrypt avec un coût de 12 est suffisant pour un contexte de microfinance,
 * même si les recommandations récentes penchent plutôt vers Argon2id.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        // Retourner 401 (non-authentifié) au lieu du 403 par défaut de Spring Security 6.
                        // Sans ça, les requêtes sans JWT valide reçoivent 403, ce qui empêche l'intercepteur
                        // Angular de déclencher le refresh automatique (qui n'écoute que les 401).
                        .authenticationEntryPoint(new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        // Endpoints publics : ping/health pour mobile, auth, docs Swagger
                        .requestMatchers("/ping", "/health", "/api/v1/ping", "/api/v1/health").permitAll()
                        .requestMatchers("/auth/**", "/api/v1/auth/**").permitAll()
                        .requestMatchers("/uploads/**", "/api/v1/uploads/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Endpoint interne pipeline Python — protégé par clé API dans le header
                        .requestMatchers("/internal/**").permitAll()
                        // Géolocalisation agents terrain
                        .requestMatchers(HttpMethod.PUT,    "/agents/me/position", "/api/v1/agents/me/position").hasRole("AGENT")
                        .requestMatchers(HttpMethod.DELETE, "/agents/me/position", "/api/v1/agents/me/position").hasRole("AGENT")
                        .requestMatchers(HttpMethod.GET, "/agents/positions", "/api/v1/agents/positions")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "ANALYSTE", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/agents/*/positions/historique", "/api/v1/agents/*/positions/historique")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "SUPER_ADMIN")
                        // Collectes terrain : rôle AGENT uniquement
                        .requestMatchers(HttpMethod.POST, "/collectes", "/api/v1/collectes").hasRole("AGENT")
                        .requestMatchers(HttpMethod.GET, "/collectes/mes-collectes", "/api/v1/collectes/mes-collectes").hasRole("AGENT")
                        .requestMatchers("/sync/**", "/api/v1/sync/**").hasRole("AGENT")
                        // SSE : tout utilisateur connecté peut s'abonner aux événements
                        .requestMatchers("/sse/**", "/api/v1/sse/**").authenticated()
                        // Changement de statut d'alerte : responsable recouvrement ou DSI
                        .requestMatchers(HttpMethod.PUT, "/alertes/**", "/api/v1/alertes/**")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DSI")
                        // Gestion de la plateforme : SUPER_ADMIN uniquement
                        .requestMatchers("/platform/**", "/api/v1/platform/**").hasRole("SUPER_ADMIN")
                        // Droits RGPD personnels : tout utilisateur connecté (art. 37-43)
                        .requestMatchers("/mes-donnees/**", "/api/v1/mes-donnees/**").authenticated()
                        // Administration RGPD, audit et violations : DSI ou SUPER_ADMIN
                        .requestMatchers("/admin/rgpd/**", "/api/v1/admin/rgpd/**").hasAnyRole("DSI", "SUPER_ADMIN")
                        .requestMatchers("/admin/audit/**", "/api/v1/admin/audit/**").hasAnyRole("DSI", "SUPER_ADMIN")
                        .requestMatchers("/admin/violations/**", "/api/v1/admin/violations/**").hasAnyRole("DSI", "SUPER_ADMIN")
                        // Étiquettes dossiers
                        .requestMatchers(HttpMethod.GET, "/dossiers/*/etiquettes", "/api/v1/dossiers/*/etiquettes")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "SUPER_ADMIN", "ANALYSTE")
                        .requestMatchers(HttpMethod.POST,   "/dossiers/*/etiquettes", "/api/v1/dossiers/*/etiquettes")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DSI", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/dossiers/*/etiquettes/*", "/api/v1/dossiers/*/etiquettes/*")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DSI", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/dossiers/etiquettes/*", "/api/v1/dossiers/etiquettes/*")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "SUPER_ADMIN")
                        // Administration des comptes : DSI ou SUPER_ADMIN
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasAnyRole("DSI", "SUPER_ADMIN")
                        // Lecture des échéances en retard : RR ou DSI
                        .requestMatchers(HttpMethod.GET, "/echeances/en-retard", "/api/v1/echeances/en-retard")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DSI")
                        // Mise à jour d'une échéance : agent, RR ou DSI
                        .requestMatchers(HttpMethod.PUT, "/echeances/**", "/api/v1/echeances/**")
                                .hasAnyRole("AGENT", "RESPONSABLE_RECOUVREMENT", "DSI")
                        // Tout le reste nécessite une authentification
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // En dev : localhost sur n'importe quel port (4200 Angular, 8080 backend, etc.)
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.imf.cm"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Requested-With",
                "X-Api-Key", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id", "X-Api-Version"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Coût 10 : ~100ms/hash — réduit de 12 pour absorber les pics de connexions simultanées
        // (terrain : ~50 agents reconnectant en même temps après coupure réseau).
        // Les hashes existants (préfixe $2a$12$) restent valides — BCrypt est rétrocompatible.
        return new BCryptPasswordEncoder(10);
    }
}
