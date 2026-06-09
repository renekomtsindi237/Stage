package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ClientResponse;
import cm.imf.pipeline.service.IClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
@Import(TestSecurityConfig.class)
@DisplayName("ClientController — tests MockMvc")
class ClientControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean IClientService clientService;

    private static final ClientResponse CLIENT =
            new ClientResponse("CLI-001", "Marie Nkomo", "+237612345678", "Agence Nord");

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/clients/search — retourne les résultats de recherche")
    void search_200() throws Exception {
        when(clientService.search("Marie", 10)).thenReturn(List.of(CLIENT));

        mockMvc.perform(get("/api/v1/clients/search").param("q", "Marie").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].idClient").value("CLI-001"))
                .andExpect(jsonPath("$.data[0].nomClient").value("Marie Nkomo"));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/clients — liste paginée")
    void list_200() throws Exception {
        when(clientService.list(0, 20)).thenReturn(List.of(CLIENT));
        when(clientService.count()).thenReturn(1L);

        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.content[0].nomClient").value("Marie Nkomo"));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/clients/{id} — détail d'un client")
    void getById_200() throws Exception {
        when(clientService.getById("CLI-001")).thenReturn(CLIENT);

        mockMvc.perform(get("/api/v1/clients/CLI-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idClient").value("CLI-001"))
                .andExpect(jsonPath("$.data.telephoneClient").value("+237612345678"));
    }
}
