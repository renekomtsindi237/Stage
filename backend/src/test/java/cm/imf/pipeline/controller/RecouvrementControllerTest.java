package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.AjouterActionRequest;
import cm.imf.pipeline.dto.request.EscaladerDossierRequest;
import cm.imf.pipeline.dto.request.OuvrirDossierRequest;
import cm.imf.pipeline.dto.response.ActionRecouvrementResponse;
import cm.imf.pipeline.dto.response.DossierRecouvrementResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.enums.CategorieCobtac;
import cm.imf.pipeline.enums.RecouvrementPhase;
import cm.imf.pipeline.enums.ResultatActionRecouvrement;
import cm.imf.pipeline.enums.TypeActionRecouvrement;
import cm.imf.pipeline.service.IRecouvrementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecouvrementController.class)
@Import(TestSecurityConfig.class)
@DisplayName("RecouvrementController — tests MockMvc (OHADA/COBAC)")
class RecouvrementControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockBean  IRecouvrementService recouvrementService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static final UUID DOSSIER_UID =
            UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private DossierRecouvrementResponse dossierResponse(RecouvrementPhase phase) {
        return new DossierRecouvrementResponse(
                DOSSIER_UID.toString(),   // uid
                "PRE-2024-001",           // idPret
                "Fomo Martin",            // nomClient
                new BigDecimal("450000"), // montantImpaye
                95,                       // joursRetard
                CategorieCobtac.DOUTEUSE, // categorieCobtac
                new BigDecimal("25"),     // tauxProvision
                new BigDecimal("112500"), // montantProvision
                null,                     // datePremiereEcheanceImpayee
                null,                     // nomCaution
                null,                     // telephoneCaution
                null,                     // typeGarantie
                BigDecimal.ZERO,          // fraisRecouvrement
                phase,                    // phase
                OffsetDateTime.now(),     // dateOuverture
                null,                     // dateDerniereAction
                null,                     // agentResponsableUsername
                false,                    // clos
                null,                     // dateCloture
                null,                     // motifCloture
                null);                    // updatedAt
    }

    private OuvrirDossierRequest ouvrirRequest() {
        return new OuvrirDossierRequest(
                "PRE-2024-001", "Fomo Martin",
                new BigDecimal("450000"), 95,
                null, null, "Moussa Talla", "691445566", null);
    }

    // ── POST /recouvrement/dossiers ───────────────────────────────────────────

    @Nested
    @DisplayName("POST /recouvrement/dossiers — ouvrir un dossier")
    class OuvrirDossier {

        @Test
        @DisplayName("→ 201 CREATED, catégorie COBAC calculée automatiquement")
        void ouvrir_valide_retourne_201() throws Exception {
            when(recouvrementService.ouvrirDossier(any(), any()))
                    .thenReturn(dossierResponse(RecouvrementPhase.RELANCE_AMIABLE));

            mockMvc.perform(post("/recouvrement/dossiers")
                            .with(TestHelper.asRr())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(ouvrirRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.idPret").value("PRE-2024-001"))
                    .andExpect(jsonPath("$.data.phase").value("RELANCE_AMIABLE"))
                    .andExpect(jsonPath("$.data.categorieCobtac").value("DOUTEUSE"));
        }

        @Test
        @DisplayName("→ 409 si dossier actif existe déjà pour ce prêt")
        void ouvrir_doublon_retourne_409() throws Exception {
            when(recouvrementService.ouvrirDossier(any(), any()))
                    .thenThrow(new ResponseStatusException(CONFLICT,
                            "Un dossier actif existe déjà pour PRE-2024-001"));

            mockMvc.perform(post("/recouvrement/dossiers")
                            .with(TestHelper.asRr())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(ouvrirRequest())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("→ 400 si montantImpaye manquant (contrainte @NotNull)")
        void ouvrir_montant_null_retourne_400() throws Exception {
            String invalid = """
                    {"idPret":"PRE-2024-001","nomClient":"Fomo","joursRetard":95}
                    """;
            mockMvc.perform(post("/recouvrement/dossiers")
                            .with(TestHelper.asRr())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("→ 403 si AGENT tente d'ouvrir un dossier")
        void ouvrir_agent_retourne_403() throws Exception {
            mockMvc.perform(post("/recouvrement/dossiers")
                            .with(TestHelper.asAgent())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(ouvrirRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    // ── GET /recouvrement/dossiers ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /recouvrement/dossiers — liste paginée")
    class ListDossiers {

        @Test
        @DisplayName("→ 200 avec liste filtrée par phase CONTENTIEUX pour RR")
        void list_par_phase_retourne_200() throws Exception {
            PageResponse<DossierRecouvrementResponse> page =
                    PageResponse.of(List.of(dossierResponse(RecouvrementPhase.CONTENTIEUX)), 0, 20, 1L);
            when(recouvrementService.listDossiers(anyLong(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(page);

            mockMvc.perform(get("/recouvrement/dossiers")
                            .with(TestHelper.asRr())
                            .param("phase", "CONTENTIEUX"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].phase").value("CONTENTIEUX"))
                    .andExpect(jsonPath("$.data.first").value(true));
        }

        @Test
        @DisplayName("→ 200 liste vide si aucun dossier en cours")
        void list_vide_retourne_200() throws Exception {
            when(recouvrementService.listDossiers(anyLong(), isNull(), isNull(), anyInt(), anyInt()))
                    .thenReturn(PageResponse.of(List.of(), 0, 20, 0L));

            mockMvc.perform(get("/recouvrement/dossiers").with(TestHelper.asRr()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.content").isEmpty());
        }
    }

    // ── GET /recouvrement/dossiers/{uid} ──────────────────────────────────────

    @Test
    @DisplayName("GET /recouvrement/dossiers/{uid} → 200 avec le détail")
    void getDossier_existant_retourne_200() throws Exception {
        when(recouvrementService.getDossier(DOSSIER_UID))
                .thenReturn(dossierResponse(RecouvrementPhase.MEDIATION_AMIABLE));

        mockMvc.perform(get("/recouvrement/dossiers/{uid}", DOSSIER_UID)
                        .with(TestHelper.asRr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uid").value(DOSSIER_UID.toString()))
                .andExpect(jsonPath("$.data.montantImpaye").value(450000));
    }

    // ── PUT /recouvrement/dossiers/{uid}/escalader ────────────────────────────

    @Test
    @DisplayName("PUT …/escalader → 200 avec nouvelle phase MISE_EN_DEMEURE")
    void escalader_mise_en_demeure_retourne_200() throws Exception {
        when(recouvrementService.escalader(eq(DOSSIER_UID), any(), any()))
                .thenReturn(dossierResponse(RecouvrementPhase.MISE_EN_DEMEURE));

        String body = """
                {"nouvellePhase":"MISE_EN_DEMEURE",
                 "motif":"Aucune réponse après 3 relances"}
                """;

        mockMvc.perform(put("/recouvrement/dossiers/{uid}/escalader", DOSSIER_UID)
                        .with(TestHelper.asRr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phase").value("MISE_EN_DEMEURE"));
    }

    // ── POST /recouvrement/dossiers/{uid}/actions ─────────────────────────────

    @Test
    @DisplayName("POST …/actions → 201 pour action VISITE_TERRAIN avec montant récupéré")
    void ajouterAction_visite_retourne_201() throws Exception {
        ActionRecouvrementResponse action = new ActionRecouvrementResponse(
                UUID.randomUUID(), DOSSIER_UID,
                TypeActionRecouvrement.VISITE_TERRAIN,
                ResultatActionRecouvrement.PAIEMENT_EFFECTUE,
                "Client rencontré à domicile",
                new BigDecimal("50000"),
                OffsetDateTime.now(), "rr_test");

        when(recouvrementService.ajouterAction(eq(DOSSIER_UID), any(), any()))
                .thenReturn(action);

        String body = """
                {"typeAction":"VISITE_TERRAIN","resultat":"PAIEMENT_EFFECTUE",
                 "observation":"Client rencontré","montantRecupere":50000}
                """;

        mockMvc.perform(post("/recouvrement/dossiers/{uid}/actions", DOSSIER_UID)
                        .with(TestHelper.asRr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.typeAction").value("VISITE_TERRAIN"))
                .andExpect(jsonPath("$.data.resultat").value("PAIEMENT_EFFECTUE"))
                .andExpect(jsonPath("$.data.montantRecupere").value(50000));
    }

    // ── PUT /recouvrement/dossiers/{uid}/clore ────────────────────────────────

    @Test
    @DisplayName("PUT …/clore → 200, dossier clos")
    void clore_dossier_retourne_200() throws Exception {
        DossierRecouvrementResponse clos = new DossierRecouvrementResponse(
                DOSSIER_UID.toString(),   // uid
                "PRE-2024-001",           // idPret
                "Fomo Martin",            // nomClient
                new BigDecimal("450000"), // montantImpaye
                95,                       // joursRetard
                CategorieCobtac.DOUTEUSE, // categorieCobtac
                new BigDecimal("25"),     // tauxProvision
                new BigDecimal("112500"), // montantProvision
                null,                     // datePremiereEcheanceImpayee
                null,                     // nomCaution
                null,                     // telephoneCaution
                null,                     // typeGarantie
                BigDecimal.ZERO,          // fraisRecouvrement
                RecouvrementPhase.RELANCE_AMIABLE, // phase
                OffsetDateTime.now(),     // dateOuverture
                null,                     // dateDerniereAction
                null,                     // agentResponsableUsername
                true,                     // clos
                OffsetDateTime.now(),     // dateCloture
                "Remboursement intégral reçu", // motifCloture
                null);                    // updatedAt

        when(recouvrementService.clore(eq(DOSSIER_UID), anyString(), any()))
                .thenReturn(clos);

        mockMvc.perform(put("/recouvrement/dossiers/{uid}/clore", DOSSIER_UID)
                        .with(TestHelper.asRr())
                        .param("motif", "Remboursement intégral reçu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clos").value(true));
    }
}
