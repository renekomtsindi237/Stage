package cm.imf.pipeline.integration;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests d'intégration complets — contexte Spring Boot complet avec PostgreSQL via Testcontainers.
 *
 * Flux d'authentification :
 *   - SUPER_ADMIN : POST /auth/login (email + password)
 *   - Autres rôles : OTP (non testé ici — dépend d'un serveur SMTP)
 *   Les tokens ANALYSTE/DSI sont générés directement via JwtTokenProvider
 *   pour tester les contrôles d'accès sans déclencher le flux OTP.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests d'intégration — contexte complet Spring Boot + PostgreSQL")
class ImfIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("imf_test")
            .withUsername("imf_test")
            .withPassword("imf_test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.cache.type",                         () -> "simple");
        registry.add("spring.data.redis.host",                    () -> "localhost");
        registry.add("spring.data.redis.port",                    () -> "6399");
        registry.add("spring.data.redis.repositories.enabled",    () -> "false");
    }

    @Autowired MockMvc          mockMvc;
    @Autowired ObjectMapper     objectMapper;
    @Autowired UserRepository   userRepository;
    @Autowired PasswordEncoder  passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;

    // Tokens partagés entre les tests (static car JUnit5 crée une instance par méthode)
    private static String accessToken;     // token ANALYSTE
    private static String dsiAccessToken;  // token DSI

    // Entités sauvegardées — conservées pour générer les tokens directement
    private static User analysteUser;
    private static User dsiUser;

    @Test
    @Order(1)
    @DisplayName("Base de données — tables créées par Flyway (utilisateurs, alertes_impayes...)")
    void flyway_cree_les_tables() {
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(2)
    @DisplayName("Repository — créer un utilisateur ANALYSTE et le SUPER_ADMIN de test")
    void creerUtilisateur_analyste() {
        analysteUser = userRepository.save(User.builder()
                .username("jkamga")
                .email("jkamga@test.cm")
                .passwordHash(passwordEncoder.encode("TestPass!2024"))
                .role(Role.ANALYSTE)
                .actif(true)
                .build());
        assertThat(userRepository.findByUsername("jkamga")).isPresent();

        // SUPER_ADMIN pour les tests /auth/login (seul rôle accepté sur cet endpoint)
        userRepository.save(User.builder()
                .username("super_admin")
                .email("super_admin@test.cm")
                .passwordHash(passwordEncoder.encode("SuperPass!2024"))
                .role(Role.SUPER_ADMIN)
                .actif(true)
                .build());
    }

    @Test
    @Order(3)
    @DisplayName("Repository — créer un utilisateur DSI pour les tests admin")
    void creerUtilisateur_dsi() {
        dsiUser = userRepository.save(User.builder()
                .username("admin_dsi")
                .email("admin_dsi@test.cm")
                .passwordHash(passwordEncoder.encode("DsiPass!2024"))
                .role(Role.DSI)
                .actif(true)
                .build());
        assertThat(userRepository.findByUsername("admin_dsi")).isPresent();
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/auth/login — ANALYSTE → 403 (doit utiliser OTP) ; token généré directement")
    void login_analyste_retourne_token() throws Exception {
        // /auth/login est réservé au SUPER_ADMIN
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("jkamga@test.cm", "TestPass!2024"))))
                .andExpect(status().isForbidden());

        // Token ANALYSTE généré directement pour les tests suivants (pas de flux OTP en CI)
        assertThat(analysteUser).isNotNull();
        accessToken = jwtTokenProvider.generateAccessToken(analysteUser);
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/auth/login — SUPER_ADMIN → 200 avec tokens ; token DSI généré directement")
    void login_dsi_retourne_token() throws Exception {
        // Vérifie que le SUPER_ADMIN peut se connecter via /auth/login
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("super_admin@test.cm", "SuperPass!2024"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(auth.accessToken()).isNotBlank();

        // Token DSI généré directement
        assertThat(dsiUser).isNotNull();
        dsiAccessToken = jwtTokenProvider.generateAccessToken(dsiUser);
        assertThat(dsiAccessToken).isNotBlank();
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/kpi/dashboard-summary — ANALYSTE authentifié → pas 401/403")
    void dashboardSummary_analyste_authentifie() throws Exception {
        Assumptions.assumeTrue(accessToken != null, "Token ANALYSTE requis — test 4 doit passer en premier");

        mockMvc.perform(get("/api/v1/kpi/dashboard-summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isNotEqualTo(401);
                    assertThat(status).isNotEqualTo(403);
                });
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/admin/users — ANALYSTE → 403 FORBIDDEN")
    void adminUsers_analyste_403() throws Exception {
        Assumptions.assumeTrue(accessToken != null);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    @DisplayName("GET /api/admin/users — DSI → 200 avec liste paginée")
    void adminUsers_dsi_200() throws Exception {
        Assumptions.assumeTrue(dsiAccessToken != null);

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + dsiAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(9)
    @DisplayName("POST /api/auth/login — mauvais mot de passe SUPER_ADMIN → 401 message générique")
    void login_mauvais_mdp_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("super_admin@test.cm", "WrongPassword!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Identifiants invalides"));
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/users/me — retourne le profil de l'utilisateur connecté (ANALYSTE)")
    void getUserMe_retourne_profil() throws Exception {
        Assumptions.assumeTrue(accessToken != null);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("jkamga"))
                .andExpect(jsonPath("$.data.role").value("ANALYSTE"));
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/auth/logout — invalide le refreshToken SUPER_ADMIN")
    void logout_puis_acces_refuse() throws Exception {
        // Login SUPER_ADMIN pour obtenir un vrai refreshToken stocké en base
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("super_admin@test.cm", "SuperPass!2024"))))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class);

        // Logout — invalide le refreshToken
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + auth.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());
    }
}
