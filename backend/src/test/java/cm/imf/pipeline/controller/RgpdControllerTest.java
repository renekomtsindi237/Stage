package cm.imf.pipeline.controller;

import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.repository.ConsentementRepository;
import cm.imf.pipeline.repository.DemandeRgpdRepository;
import cm.imf.pipeline.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests RgpdController — Loi 2024/017 Cameroun art. 37-43.
 * Note : RgpdController dépend directement des repositories (pas de service intermédiaire).
 */
@WebMvcTest(RgpdController.class)
@Import(TestSecurityConfig.class)
@DisplayName("RgpdController — droits RGPD Loi 2024/017")
class RgpdControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    // RgpdController utilise les repositories directement
    @MockBean DemandeRgpdRepository    demandeRepository;
    @MockBean ConsentementRepository   consentementRepository;
    @MockBean UserRepository           userRepository;

    // ── POST /mes-donnees/demande ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /mes-donnees/demande → 201 — tout utilisateur authentifié peut soumettre")
    void soumettreDemande_authentifie_retourne_201() throws Exception {
        User user = TestHelper.mockAnalyste();
        cm.imf.pipeline.entity.DemandeRgpd saved = new cm.imf.pipeline.entity.DemandeRgpd();
        saved.setId(1L);
        saved.setTypeDroit("ACCES");
        when(demandeRepository.save(any())).thenReturn(saved);

        String body = """
                {"typeDroit":"ACCES","perimetre":"Toutes mes données",
                 "finaliteConcernee":"MARKETING"}
                """;

        mockMvc.perform(post("/mes-donnees/demande")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.authentication(
                                new org.springframework.security.authentication
                                        .UsernamePasswordAuthenticationToken(
                                        user, null, user.getAuthorities())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(demandeRepository).save(any());
    }

    @Test
    @DisplayName("POST /mes-donnees/demande → 401 si non authentifié")
    void soumettreDemande_non_authentifie_retourne_401() throws Exception {
        mockMvc.perform(post("/mes-donnees/demande")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeDroit\":\"ACCES\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /mes-donnees/demandes ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /mes-donnees/demandes → 200 — liste des demandes de l'utilisateur")
    void listMesDemandes_retourne_200() throws Exception {
        User user = TestHelper.mockAnalyste();
        when(demandeRepository.findByDemandeurIdOrderByDateSoumissionDesc(any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/mes-donnees/demandes")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.authentication(
                                new org.springframework.security.authentication
                                        .UsernamePasswordAuthenticationToken(
                                        user, null, user.getAuthorities()))))
                .andExpect(status().isOk());
    }

    // ── Admin RGPD : DSI seulement ────────────────────────────────────────────

    @Test
    @DisplayName("GET /admin/rgpd/demandes → 200 pour DSI")
    void listAllDemandes_dsi_retourne_200() throws Exception {
        when(demandeRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

        mockMvc.perform(get("/admin/rgpd/demandes").with(TestHelper.asDsi()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /admin/rgpd/demandes → 403 pour ANALYSTE")
    void listAllDemandes_analyste_retourne_403() throws Exception {
        mockMvc.perform(get("/admin/rgpd/demandes").with(TestHelper.asAnalyste()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin/rgpd/demandes/en-retard → 200 pour DSI — SLA art. 41")
    void demandesEnRetard_dsi_retourne_200() throws Exception {
        when(demandeRepository.findEnRetard(any(), any()))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/admin/rgpd/demandes/en-retard").with(TestHelper.asDsi()))
                .andExpect(status().isOk());
    }
}
