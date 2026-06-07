package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.EcheanceResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.enums.StatutEcheance;
import cm.imf.pipeline.service.IEcheanceService;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EcheanceController.class)
@Import(TestSecurityConfig.class)
@DisplayName("EcheanceController — tests MockMvc")
class EcheanceControllerTest {

    private static final UUID ECH_UID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean IEcheanceService echeanceService;

    private EcheanceResponse buildResponse() {
        return new EcheanceResponse(
                null, "PRE-001", null, null, 1,
                LocalDate.now().plusMonths(1),
                new BigDecimal("50000"), BigDecimal.ZERO, new BigDecimal("50000"),
                null, StatutEcheance.EN_ATTENTE, null, null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/echeances/pret/{id} — retourne 200 avec liste")
    void getByPret_200() throws Exception {
        when(echeanceService.getByPret("PRE-001")).thenReturn(List.of(buildResponse()));

        mockMvc.perform(get("/echeances/pret/PRE-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].idPret").value("PRE-001"))
                .andExpect(jsonPath("$.data[0].statut").value("EN_ATTENTE"));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/echeances/{id} — retourne 200 avec détail")
    void getById_200() throws Exception {
        when(echeanceService.getById(any(UUID.class))).thenReturn(buildResponse());

        mockMvc.perform(get("/echeances/{uid}", ECH_UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idPret").value("PRE-001"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("PUT /api/echeances/{id} — agent peut mettre à jour")
    void updateStatut_200() throws Exception {
        when(echeanceService.updateStatut(any(UUID.class), any())).thenReturn(buildResponse());

        String body = """
                {"statut":"PAYEE","montantPaye":50000,"datePaiement":"2026-04-03","observation":"OK"}
                """;

        mockMvc.perform(put("/echeances/{uid}", ECH_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RESPONSABLE_RECOUVREMENT")
    @DisplayName("GET /api/echeances/en-retard — RR peut accéder")
    void getEnRetard_rr_200() throws Exception {
        when(echeanceService.getEcheancesEnRetard(0, 20))
                .thenReturn(PageResponse.of(List.of(), 0, 20, 0L));

        mockMvc.perform(get("/echeances/en-retard"))
                .andExpect(status().isOk());
    }
}
