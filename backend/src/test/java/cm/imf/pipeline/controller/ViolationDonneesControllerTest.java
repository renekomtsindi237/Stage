package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.service.IAuditTrailService;
import cm.imf.pipeline.service.INotificationService;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests ViolationDonneesController — art. 22 Loi 2024/017 Cameroun (délai 72h).
 * L'accès est réservé aux rôles DSI et SUPER_ADMIN (@PreAuthorize classe).
 */
@WebMvcTest(ViolationDonneesController.class)
@Import(TestSecurityConfig.class)
@DisplayName("ViolationDonneesController — registre violations données (art. 22)")
class ViolationDonneesControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockBean  JdbcTemplate         jdbc;
    @MockBean  IAuditTrailService   auditTrailService;
    @MockBean  INotificationService notificationService;
    @MockBean  SseEmitterRegistry   sseRegistry;

    // ── POST /admin/violations ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /admin/violations → 201 pour DSI — déclaration dans les 72h")
    void declarer_dsi_retourne_201() throws Exception {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(42L);
        doNothing().when(notificationService).sendPushToRole(any(), anyString(), anyString());
        doNothing().when(sseRegistry).broadcast(any(SseEventDto.class));

        String body = """
                {"typeViolation":"ACCES_NON_AUTORISE",
                 "descriptionViolation":"Accès base de données depuis IP externe",
                 "nombrePersonnesConcernees":150,
                 "categoriesDonnees":["IDENTITE","FINANCIER"],
                 "dateDecouverte":"2026-05-19T08:00:00Z",
                 "mesuresPrisesImmediatement":"Blocage IP, revue logs"}
                """;

        mockMvc.perform(post("/api/v1/admin/violations")
                        .with(TestHelper.asDsi())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /admin/violations → 403 pour AGENT")
    void declarer_agent_retourne_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/violations")
                        .with(TestHelper.asAgent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeViolation\":\"ACCES_NON_AUTORISE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /admin/violations → 403 pour ANALYSTE")
    void declarer_analyste_retourne_403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/violations")
                        .with(TestHelper.asAnalyste())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeViolation\":\"FUITE\"}"))
                .andExpect(status().isForbidden());
    }

    // ── GET /admin/violations ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /admin/violations → 200 pour DSI")
    void list_dsi_retourne_200() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 1, "type_violation", "ACCES_NON_AUTORISE")));

        mockMvc.perform(get("/api/v1/admin/violations").with(TestHelper.asDsi()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/violations → 200 pour SUPER_ADMIN (cross-IMF)")
    void list_superAdmin_retourne_200() throws Exception {
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/violations").with(TestHelper.asSuperAdmin()))
                .andExpect(status().isOk());
    }
}
