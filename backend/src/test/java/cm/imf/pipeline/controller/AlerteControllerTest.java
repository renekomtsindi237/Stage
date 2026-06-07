package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.AlerteUpdateRequest;
import cm.imf.pipeline.dto.response.AlerteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.enums.StatutAlerte;
import cm.imf.pipeline.service.AlerteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlerteController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AlerteController — tests MockMvc")
class AlerteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  AlerteService alerteService;

    private AlerteResponse sampleAlerte() {
        return new AlerteResponse(1L, "PRE-001", OffsetDateTime.now(), 35,
                new BigDecimal("150000"), StatutAlerte.ACTIVE, false, false, null);
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/alertes — retourne liste paginée")
    void getAlertes_retourne_page() throws Exception {
        PageResponse<AlerteResponse> page = new PageResponse<>(List.of(sampleAlerte()), 0, 20, 1, 1, true);
        when(alerteService.getAlertes(null, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/alertes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].idPret").value("PRE-001"));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/alertes?statut=ACTIVE — filtre par statut")
    void getAlertes_avec_filtre_statut() throws Exception {
        PageResponse<AlerteResponse> page = new PageResponse<>(List.of(sampleAlerte()), 0, 20, 1, 1, true);
        when(alerteService.getAlertes(eq(StatutAlerte.ACTIVE), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/alertes").param("statut", "ACTIVE"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/alertes/{id} — retourne l'alerte")
    void getById_retourne_alerte() throws Exception {
        when(alerteService.getById(1L)).thenReturn(sampleAlerte());

        mockMvc.perform(get("/alertes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joursRetard").value(35));
    }

    @Test
    @WithMockUser(roles = "RESPONSABLE_RECOUVREMENT")
    @DisplayName("PUT /api/alertes/{id} — RR peut clôturer une alerte")
    void updateStatut_rr_peut_cloturer() throws Exception {
        AlerteResponse cloturee = new AlerteResponse(1L, "PRE-001", OffsetDateTime.now(), 35,
                new BigDecimal("150000"), StatutAlerte.CLOTUREE, false, false, OffsetDateTime.now());
        when(alerteService.updateStatut(eq(1L), any())).thenReturn(cloturee);

        mockMvc.perform(put("/alertes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AlerteUpdateRequest(StatutAlerte.CLOTUREE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutAlerte").value("CLOTUREE"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("PUT /api/alertes/{id} — AGENT ne peut pas modifier une alerte → 403")
    void updateStatut_agent_refuse_403() throws Exception {
        mockMvc.perform(put("/alertes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AlerteUpdateRequest(StatutAlerte.CLOTUREE))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/alertes — non authentifié → 401")
    void getAlertes_non_authentifie_401() throws Exception {
        mockMvc.perform(get("/alertes"))
                .andExpect(status().isUnauthorized());
    }
}
