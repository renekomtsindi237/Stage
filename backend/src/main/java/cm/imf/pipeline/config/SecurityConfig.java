package cm.imf.pipeline.config;

import cm.imf.pipeline.filter.ApiKeyAuthenticationFilter;
import cm.imf.pipeline.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

import java.util.ArrayList;
import java.util.Arrays;
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
 * CORS : origines lues depuis app.cors.allowed-origins (CORS_ALLOWED_ORIGINS).
 * Inclut localhost, le domaine public, et les origines Tauri du client bureau.
 *
 * BCrypt avec un coût de 12 est suffisant pour un contexte de microfinance,
 * même si les recommandations récentes penchent plutôt vers Argon2id.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter    jwtAuthFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthFilter;
    private final UserDetailsService         userDetailsService;

    @Value("${app.cors.allowed-origins}")
    private String corsAllowedOrigins;

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
                        // Les dispatches d'erreur Tomcat (/error) doivent être permis —
                        // sinon Spring Security relance AccessDeniedException sur le dispatch
                        // ERROR, ce qui corrompt la réponse et produit un 500 au lieu du 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Endpoints publics : ping/health pour mobile, auth, docs Swagger
                        .requestMatchers("/ping", "/health", "/api/v1/ping", "/api/v1/health").permitAll()
                        .requestMatchers("/auth/**", "/api/v1/auth/**").permitAll()
                        .requestMatchers("/uploads/**", "/api/v1/uploads/**").permitAll()
                        .requestMatchers("/public/**", "/api/v1/public/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs", "/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Endpoint interne pipeline Python — protégé par clé API dans le header
                        .requestMatchers("/internal/**", "/api/v1/internal/**").permitAll()
                        // Géolocalisation agents terrain
                        .requestMatchers(HttpMethod.PUT,    "/agents/me/position", "/api/v1/agents/me/position").hasRole("AGENT")
                        .requestMatchers(HttpMethod.DELETE, "/agents/me/position", "/api/v1/agents/me/position").hasRole("AGENT")
                        .requestMatchers(HttpMethod.GET, "/agents/positions", "/api/v1/agents/positions")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "ANALYSTE", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/agents/*/positions/historique", "/api/v1/agents/*/positions/historique")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "SUPER_ADMIN")
                        // Import/export CSV clients
                        .requestMatchers(HttpMethod.GET, "/clients/template", "/api/v1/clients/template").authenticated()
                        .requestMatchers(HttpMethod.POST, "/clients/import", "/api/v1/clients/import")
                                .hasAnyRole("AGENT_CREDIT", "CHEF_AGENCE", "DSI", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/clients/export", "/api/v1/clients/export").authenticated()
                        // Dashboard agent terrain
                        .requestMatchers(HttpMethod.GET, "/agent/dashboard", "/api/v1/agent/dashboard").hasRole("AGENT")
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
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "SUPER_ADMIN", "ANALYSTE",
                                        "AGENT_CREDIT", "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS")
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
                                .hasAnyRole("AGENT", "AGENT_CREDIT", "RESPONSABLE_RECOUVREMENT", "DSI")
                        // External API — authentification par X-Api-Key (filtre ApiKeyAuthenticationFilter)
                        .requestMatchers("/external/**", "/api/v1/external/**").hasRole("API_CLIENT")
                        // Gestion des clés API : SUPPORT et SUPER_ADMIN
                        .requestMatchers("/support/api-clients/**", "/api/v1/support/api-clients/**")
                                .hasAnyRole("SUPPORT", "SUPER_ADMIN")
                        // Tableau de bord infrastructure SUPPORT (cross-IMF)
                        .requestMatchers("/api/v1/support/**").hasRole("SUPPORT")
                        // Tickets : création par tout utilisateur, lecture/maj par SUPPORT
                        .requestMatchers(HttpMethod.POST, "/api/v1/tickets").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tickets/mes-tickets").authenticated()
                        .requestMatchers("/api/v1/tickets/**").hasAnyRole("SUPPORT", "SUPER_ADMIN")
                        // DSI : endpoints dédiés au tableau de bord DSI
                        .requestMatchers("/api/v1/dsi/**").hasAnyRole("DSI", "SUPER_ADMIN")
                        // Analyste : scoring, pipeline, drift ML, risk PAR
                        .requestMatchers("/api/v1/analyste/**")
                                .hasAnyRole("ANALYSTE", "ANALYSTE_ENGAGEMENTS", "DSI", "DIRECTEUR", "SUPER_ADMIN")
                        // Octroi crédit — dossiers, garanties, comité, visite J+15
                        .requestMatchers("/api/v1/dossiers-credit/**")
                                .hasAnyRole("AGENT_CREDIT", "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS",
                                        "DIRECTEUR", "DSI", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/garanties/**")
                                .hasAnyRole("AGENT_CREDIT", "CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS",
                                        "DSI", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/comite/**")
                                .hasAnyRole("CHEF_AGENCE", "ANALYSTE_ENGAGEMENTS", "DIRECTEUR",
                                        "DSI", "SUPER_ADMIN")
                        .requestMatchers("/api/v1/visites-conformite/**")
                                .hasAnyRole("AGENT_CREDIT", "CHEF_AGENCE", "DSI", "SUPER_ADMIN")
                        // Back-office : contrats, signatures
                        .requestMatchers("/api/v1/back-office/**")
                                .hasAnyRole("AGENT_SAISIE", "CHEF_AGENCE", "DIRECTEUR", "DSI", "SUPER_ADMIN")
                        // Caisse : décaissements, encaissements
                        .requestMatchers("/api/v1/caisse/**")
                                .hasAnyRole("CAISSIER", "DIRECTEUR", "DSI", "SUPER_ADMIN")
                        // Plans d'apurement (recouvrement amiable)
                        .requestMatchers("/api/v1/plans-apurement/**")
                                .hasAnyRole("AGENT", "AGENT_CREDIT", "RESPONSABLE_RECOUVREMENT",
                                        "DSI", "SUPER_ADMIN")
                        // Contentieux OHADA
                        .requestMatchers("/api/v1/contentieux/**")
                                .hasAnyRole("RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "SUPER_ADMIN")
                        // Délégations hiérarchiques : lecture ouverte à tout utilisateur authentifié,
                        // écriture restreinte aux rôles managériaux (vérifiée en @PreAuthorize)
                        .requestMatchers("/api/v1/delegations/**").authenticated()
                        // Tout le reste nécessite une authentification
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = new ArrayList<>(Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        for (String desktopOrigin : List.of(
                "http://tauri.localhost",
                "https://tauri.localhost",
                "tauri://localhost",
                "http://asset.localhost",
                "https://asset.localhost",
                "asset://localhost")) {
            if (!origins.contains(desktopOrigin)) {
                origins.add(desktopOrigin);
            }
        }
        config.setAllowedOriginPatterns(origins);
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
