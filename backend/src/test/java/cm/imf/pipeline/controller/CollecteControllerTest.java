package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CollecteRequest;
import cm.imf.pipeline.dto.response.CollecteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.enums.StatutCollecte;
import cm.imf.pipeline.service.CollecteService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CollecteController.class)
@Import(TestSecurityConfig.class)
@DisplayName("CollecteController — tests MockMvc")
class CollecteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  CollecteService collecteService;

    private CollecteRequest validRequest() {
        return new CollecteRequest("MOBILE-001", "CLI001", "PRE001", LocalDate.now(),
                new BigDecimal("25000"), CanalPaiement.MTN,
                "REF001", null, null, null);
    }

    private CollecteResponse confirmedResponse() {
        return new CollecteResponse(1L, "MOBILE-001", "CLI001", "PRE001",
                LocalDate.now(), new BigDecimal("25000"), CanalPaiement.MTN,
                "REF001", StatutCollecte.CONFIRMEE, OffsetDateTime.now());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /api/collectes — AGENT enregistre collecte → 201 CREATED")
    void enregistrer_collecte_agent_201() throws Exception {
        when(collecteService.enregistrer(any(), any())).thenReturn(confirmedResponse());

        mockMvc.perform(post("/collectes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("CONFIRMEE"))
                .andExpect(jsonPath("$.idCollecteMobile").value("MOBILE-001"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /api/collectes — doublon → 409 CONFLICT")
    void enregistrer_doublon_409() throws Exception {
        CollecteResponse doublon = new CollecteResponse(1L, "MOBILE-001", "CLI001", "PRE001",
                LocalDate.now(), new BigDecimal("25000"), CanalPaiement.MTN,
                "REF001", StatutCollecte.DOUBLON, OffsetDateTime.now());
        when(collecteService.enregistrer(any(), any())).thenReturn(doublon);

        mockMvc.perform(post("/collectes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("POST /api/collectes — ANALYSTE n'a pas le droit → 403")
    void enregistrer_analyste_403() throws Exception {
        mockMvc.perform(post("/collectes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /api/collectes/mes-collectes — retourne page paginée")
    void getMesCollectes_agent_ok() throws Exception {
        PageResponse<CollecteResponse> page = new PageResponse<>(
                List.of(confirmedResponse()), 0, 20, 1, 1, true);
        when(collecteService.getMesCollectes(any(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/collectes/mes-collectes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/collectes/{id} — ANALYSTE peut voir une collecte")
    void getById_analyste_ok() throws Exception {
        when(collecteService.getById(1L)).thenReturn(confirmedResponse());

        mockMvc.perform(get("/collectes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
