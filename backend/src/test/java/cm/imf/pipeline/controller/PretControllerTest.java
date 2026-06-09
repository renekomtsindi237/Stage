package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.PretResponse;
import cm.imf.pipeline.service.IPretService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PretController.class)
@Import(TestSecurityConfig.class)
@DisplayName("PretController — tests MockMvc")
class PretControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean IPretService pretService;

    private PretResponse buildPret() {
        return new PretResponse(
                "PRE-001", "CLI-001", "Jean Dupont",
                "Agence Nord", "Crédit Standard", "Agent A",
                new BigDecimal("500000"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1),
                new BigDecimal("100000"), new BigDecimal("400000"),
                "EN_RETARD", 45);
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/prets — retourne 200 avec contenu et total")
    void listPrets_200() throws Exception {
        when(pretService.listPrets(null, 0, 20)).thenReturn(List.of(buildPret()));
        when(pretService.countPrets(null)).thenReturn(1L);

        mockMvc.perform(get("/api/v1/prets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.content[0].idPret").value("PRE-001"));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/prets/{id} — retourne 200 avec détail du prêt")
    void getById_200() throws Exception {
        when(pretService.getById("PRE-001")).thenReturn(buildPret());

        mockMvc.perform(get("/api/v1/prets/PRE-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idPret").value("PRE-001"))
                .andExpect(jsonPath("$.data.joursRetard").value(45));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/prets/client/{idClient} — retourne les prêts du client")
    void getPretsClient_200() throws Exception {
        when(pretService.getPretsClient("CLI-001")).thenReturn(List.of(buildPret()));

        mockMvc.perform(get("/api/v1/prets/client/CLI-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].idPret").value("PRE-001"));
    }

    @Test
    @WithMockUser(username = "agent01", roles = "AGENT")
    @DisplayName("GET /api/prets/mes-prets — agent voit ses prêts")
    void getMesPrets_200() throws Exception {
        when(pretService.getPretsAgent("agent01")).thenReturn(List.of(buildPret()));

        mockMvc.perform(get("/api/v1/prets/mes-prets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].idPret").value("PRE-001"));
    }
}
