package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ImportResultResponse;
import cm.imf.pipeline.service.IClientImportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientImportController.class)
@Import(TestSecurityConfig.class)
@DisplayName("ClientImportController — tests MockMvc")
class ClientImportControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  IClientImportService importService;

    @Test
    @WithMockUser(roles = "DSI")
    @DisplayName("GET /clients/template — retourne fichier CSV avec en-têtes")
    void getTemplate_ok() throws Exception {
        String csv = "client_id_externe;nom_complet;telephone_principal\n";
        when(importService.genererTemplateCsv()).thenReturn(csv);

        mockMvc.perform(get("/api/v1/clients/template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"modele_import_clients.csv\""));
    }

    @Test
    @DisplayName("GET /clients/template — non authentifié → 401")
    void getTemplate_nonAuthentifie_401() throws Exception {
        mockMvc.perform(get("/api/v1/clients/template"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "AGENT_CREDIT")
    @DisplayName("POST /clients/import — fichier valide → résumé import")
    void importCsv_ok() throws Exception {
        ImportResultResponse result = new ImportResultResponse(2, 2, 0, 0, List.of());
        when(importService.importerDepuisCsv(any(), anyString())).thenReturn(result);

        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "clients.csv",
                "text/csv",
                "client_id_externe;nom_complet\nCLF001;Test Client\n".getBytes());

        mockMvc.perform(multipart("/api/v1/clients/import").file(fichier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalLignes").value(2))
                .andExpect(jsonPath("$.data.importe").value(2))
                .andExpect(jsonPath("$.data.erreurs").value(0));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /clients/import — AGENT n'a pas les droits → 403")
    void importCsv_agentInterdit_403() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "clients.csv", "text/csv", "data".getBytes());

        mockMvc.perform(multipart("/api/v1/clients/import").file(fichier))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DSI")
    @DisplayName("POST /clients/import — fichier vide → 400")
    void importCsv_fichierVide_400() throws Exception {
        MockMultipartFile fichier = new MockMultipartFile(
                "fichier", "vide.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/v1/clients/import").file(fichier))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /clients/export — retourne CSV clients de l'agent")
    void exportClients_ok() throws Exception {
        String csv = "client_id_externe;nom_complet\nCLF001;Astride FOUDA\n";
        when(importService.exporterClientsAgent(anyString(), anyString())).thenReturn(csv);

        mockMvc.perform(get("/api/v1/clients/export")
                        .param("agentEmail", "renekomtsindi99@gmail.com")
                        .param("imfCode", "FINANCE"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @DisplayName("GET /clients/export — non authentifié → 401")
    void exportClients_nonAuthentifie_401() throws Exception {
        mockMvc.perform(get("/api/v1/clients/export")
                        .param("agentEmail", "renekomtsindi99@gmail.com"))
                .andExpect(status().isUnauthorized());
    }
}
