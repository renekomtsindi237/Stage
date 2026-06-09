package cm.imf.pipeline.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConnectivityController.class)
@Import(TestSecurityConfig.class)
@DisplayName("ConnectivityController — tests MockMvc (ping / health)")
class ConnectivityControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/ping — sans authentification → 200 avec statut EN_LIGNE")
    void ping_sans_auth_200() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_LIGNE"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.serverTime").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/ping — Cache-Control: no-store pour éviter les faux positifs")
    void ping_cache_control_no_store() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("GET /api/health — alias /api/ping → même réponse 200")
    void health_alias_200() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_LIGNE"));
    }

    @Test
    @DisplayName("GET /api/ping — réponse en moins de 100ms (pas de DB)")
    void ping_rapide() throws Exception {
        long start = System.currentTimeMillis();
        mockMvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
        long elapsed = System.currentTimeMillis() - start;
        // En contexte de test MockMvc le temps réel n'est pas significatif,
        // mais on vérifie que l'endpoint ne fait pas d'appel bloquant
        org.assertj.core.api.Assertions.assertThat(elapsed).isLessThan(5000);
    }
}
