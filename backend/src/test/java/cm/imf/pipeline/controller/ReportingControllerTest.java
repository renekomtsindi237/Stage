package cm.imf.pipeline.controller;

import cm.imf.pipeline.service.ExportService;
import cm.imf.pipeline.service.PdfExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportingController.class)
@Import(TestSecurityConfig.class)
@DisplayName("ReportingController — tests MockMvc (exports CSV et PDF)")
class ReportingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  ExportService exportService;
    @MockBean  PdfExportService pdfExportService;

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/reporting/collectes/csv — retourne fichier CSV avec Content-Disposition")
    void exportCollectesCSV_ok() throws Exception {
        when(exportService.exportCollectesCSV(any(), any()))
                .thenReturn("date_collecte;canal;agence\n2024-01-15;MTN;Agence Nord\n");

        mockMvc.perform(get("/api/v1/reporting/collectes/csv")
                        .param("dateDebut", "2024-01-01")
                        .param("dateFin", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        containsString(".csv")));
    }

    @Test
    @WithMockUser(roles = "DIRECTEUR")
    @DisplayName("GET /api/reporting/prets-retard/csv — retourne CSV prêts en retard")
    void exportPretsRetardCSV_ok() throws Exception {
        when(exportService.exportPretsEnRetardCSV())
                .thenReturn("id_pret;id_client;nom_client\nPRE001;CLI001;Jean Dupont\n");

        mockMvc.perform(get("/api/v1/reporting/prets-retard/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/reporting/collectes/pdf — retourne PDF avec Content-Type application/pdf")
    void exportCollectesPDF_ok() throws Exception {
        byte[] fakePdf = "%PDF-1.4 fake content".getBytes();
        when(pdfExportService.exportCollectesPDF(any(), any())).thenReturn(fakePdf);

        mockMvc.perform(get("/api/v1/reporting/collectes/pdf")
                        .param("dateDebut", "2024-01-01")
                        .param("dateFin", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        containsString(".pdf")));
    }

    @Test
    @WithMockUser(roles = "DIRECTEUR")
    @DisplayName("GET /api/reporting/kpi/pdf — retourne PDF KPI")
    void exportKpiPDF_ok() throws Exception {
        byte[] fakePdf = "%PDF-1.4 kpi".getBytes();
        when(pdfExportService.exportKpiRapportPDF(any(), any())).thenReturn(fakePdf);

        mockMvc.perform(get("/api/v1/reporting/kpi/pdf")
                        .param("dateDebut", "2024-01-01")
                        .param("dateFin", "2024-01-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("GET /api/reporting/collectes/csv — non authentifié → 401")
    void exportCSV_non_authentifie_401() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/collectes/csv")
                        .param("dateDebut", "2024-01-01")
                        .param("dateFin", "2024-01-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/reporting/collectes/csv — dateDebut manquante → 400")
    void exportCSV_param_manquant_400() throws Exception {
        mockMvc.perform(get("/api/v1/reporting/collectes/csv")
                        .param("dateFin", "2024-01-31"))
                .andExpect(status().isBadRequest());
    }
}
