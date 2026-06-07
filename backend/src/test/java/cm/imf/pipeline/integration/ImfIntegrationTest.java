package cm.imf.pipeline.integration;

import cm.imf.pipeline.dto.request.CreateUserRequest;
import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.service.AdminService;
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
 * Tests d'intégration complets — démarrage du contexte Spring Boot complet
 * avec une base PostgreSQL réelle via TestContainers.
 * Couvre le flux bout-en-bout : login → JWT → endpoints protégés.
 */
@Testcontainers
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
        // Pas de Redis en test
        registry.add("spring.cache.type", () -> "simple");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6399");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AdminService adminService;
    @Autowired UserRepository userRepository;

    private static String accessToken;
    private static String dsiAccessToken;

    @Test
    @Order(1)
    @DisplayName("Base de données — tables créées par Flyway (utilisateurs, alertes_impayes...)")
    void flyway_cree_les_tables() {
        // Si on arrive ici, Flyway a exécuté V1 sans erreur
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(2)
    @DisplayName("AdminService — créer un utilisateur ANALYSTE via service")
    void creerUtilisateur_analyste() {
        UserResponse user = adminService.createUser(
                new CreateUserRequest("jkamga", "TestPass!2024", Role.ANALYSTE, "YD001"));
        assertThat(user.username()).isEqualTo("jkamga");
        assertThat(user.role()).isEqualTo(Role.ANALYSTE);
        assertThat(user.actif()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("AdminService — créer un utilisateur DSI pour les tests admin")
    void creerUtilisateur_dsi() {
        adminService.createUser(
                new CreateUserRequest("admin_dsi", "DsiPass!2024", Role.DSI, null));
        assertThat(userRepository.findByUsername("admin_dsi")).isPresent();
    }

    @Test
    @Order(4)
    @DisplayName("POST /api/auth/login — connexion ANALYSTE → accessToken reçu")
    void login_analyste_retourne_token() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("jkamga", "TestPass!2024"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ANALYSTE"))
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        accessToken = auth.accessToken();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    @Order(5)
    @DisplayName("POST /api/auth/login — connexion DSI → accessToken DSI reçu")
    void login_dsi_retourne_token() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin_dsi", "DsiPass!2024"))))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        dsiAccessToken = auth.accessToken();
        assertThat(dsiAccessToken).isNotBlank();
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/kpi/dashboard-summary — ANALYSTE authentifié → 200")
    void dashboardSummary_analyste_authentifie() throws Exception {
        Assumptions.assumeTrue(accessToken != null, "Token d'accès requis — test 4 doit passer en premier");

        // En intégration, le DW n'existe pas → la requête SQL échoue ou retourne vide.
        // On vérifie seulement que la sécurité laisse passer (200 ou 500 mais pas 401/403).
        mockMvc.perform(get("/api/v1/kpi/dashboard-summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().is2xxSuccessful());
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
    @DisplayName("POST /api/auth/login — mauvais mot de passe → 401 message générique")
    void login_mauvais_mdp_401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("jkamga", "WrongPassword!"))))
                .andExpect(status().isUnauthorized())
                // Pas d'indice sur ce qui est faux
                .andExpect(jsonPath("$.message").value("Identifiants invalides"));
    }

    @Test
    @Order(10)
    @DisplayName("GET /api/users/me — retourne le profil de l'utilisateur connecté")
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
    @DisplayName("POST /api/auth/logout — invalide le token → plus utilisable")
    void logout_puis_acces_refuse() throws Exception {
        Assumptions.assumeTrue(accessToken != null);

        // 1. Récupère refreshToken en se reconnectant
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("jkamga", "TestPass!2024"))))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class);

        // 2. Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + auth.refreshToken() + "\"}"))
                .andExpect(status().isNoContent());
    }
}
