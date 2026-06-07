package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.AgentResponse;
import cm.imf.pipeline.service.IAgentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AgentController — tests MockMvc")
class AgentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean IAgentService agentService;

    private static final AgentResponse AGENT = new AgentResponse(
            "AG001", null, "Amadou Diallo", "ANC01", "Agence Nord",
            null, null, null, null, null, false, false, null);

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/agents — retourne 200 avec contenu et total")
    void listAll_retourne_200() throws Exception {
        when(agentService.listAll(0, 20)).thenReturn(List.of(AGENT));
        when(agentService.count()).thenReturn(1L);

        mockMvc.perform(get("/agents").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.content[0].idAgent").value("AG001"));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/agents/agence/{id} — retourne la liste des agents de l'agence")
    void listByAgence_retourne_agents() throws Exception {
        when(agentService.listByAgence("ANC01")).thenReturn(List.of(AGENT));

        mockMvc.perform(get("/agents/agence/ANC01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nomAgent").value("Amadou Diallo"));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/agents/{id} — retourne le détail de l'agent")
    void getById_retourne_agent() throws Exception {
        when(agentService.getById("AG001")).thenReturn(AGENT);

        mockMvc.perform(get("/agents/AG001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idAgent").value("AG001"))
                .andExpect(jsonPath("$.data.nomAgence").value("Agence Nord"));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/agents/search — retourne les résultats de recherche")
    void search_retourne_resultats() throws Exception {
        when(agentService.search("Amadou", 10)).thenReturn(List.of(AGENT));

        mockMvc.perform(get("/agents/search").param("q", "Amadou").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].idAgent").value("AG001"));
    }
}
