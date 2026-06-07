package cm.imf.pipeline.controller;

import cm.imf.pipeline.service.IKpiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KpiController.class)
@Import(TestSecurityConfig.class)
@DisplayName("KpiController — tests MockMvc")
class KpiControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  IKpiService kpiService;

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/kpi/par-stats — retourne liste de stats PAR")
    void getParStats_ok() throws Exception {
        when(kpiService.getParStats(any(), any())).thenReturn(List.of(
                Map.of("zone_id", "YD", "par30", 0.12, "par90", 0.05)));

        mockMvc.perform(get("/kpi/par-stats")
                        .param("dateDebut", "2024-01-01")
                        .param("dateFin", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zone_id").value("YD"));
    }

    @Test
    @WithMockUser(roles = "DIRECTEUR")
    @DisplayName("GET /api/kpi/dashboard-summary — retourne résumé tableau de bord")
    void getDashboardSummary_ok() throws Exception {
        when(kpiService.getDashboardSummary()).thenReturn(Map.of(
                "totalCollectes", 1250,
                "montantTotal", 31250000,
                "nbAlertesActives", 18));

        mockMvc.perform(get("/kpi/dashboard-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCollectes").value(1250))
                .andExpect(jsonPath("$.nbAlertesActives").value(18));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/kpi/par-stats — dateDebut manquante → 400")
    void getParStats_date_manquante_400() throws Exception {
        mockMvc.perform(get("/kpi/par-stats")
                        .param("dateFin", "2024-01-31"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/kpi/dashboard-summary — non authentifié → 401")
    void getDashboardSummary_non_authentifie_401() throws Exception {
        mockMvc.perform(get("/kpi/dashboard-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /api/kpi/par-stats — AGENT peut accéder au KPI (authenticated)")
    void getParStats_agent_ok() throws Exception {
        when(kpiService.getParStats(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/kpi/par-stats")
                        .param("dateDebut", "2024-01-01")
                        .param("dateFin", "2024-01-31"))
                .andExpect(status().isOk());
    }
}
