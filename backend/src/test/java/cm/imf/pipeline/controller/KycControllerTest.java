package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.EvaluerRisqueKycRequest;
import cm.imf.pipeline.dto.request.InitierKycRequest;
import cm.imf.pipeline.dto.request.VerifierKycRequest;
import cm.imf.pipeline.dto.response.KycDossierResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.NiveauRisque;
import cm.imf.pipeline.enums.ResultatVerificationKyc;
import cm.imf.pipeline.enums.StatutKyc;
import cm.imf.pipeline.service.IKycService;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KycController.class)
@Import(TestSecurityConfig.class)
@DisplayName("KycController — tests MockMvc")
class KycControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockBean  IKycService kycService;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static UUID DOSSIER_UID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private InitierKycRequest initierRequest() {
        return new InitierKycRequest(
                "CLI-001", "Kouam", "Jean-Pierre",
                LocalDate.of(1990, 3, 15), "Yaoundé", "Camerounaise",
                "697112233", "kouam@imf.cm", "Biyem-Assi", "Yaoundé",
                "Commerçant", "Auto-emploi", null,
                null, null, null, null, null,
                NiveauKyc.NIVEAU_1, false, null);
    }

    private KycDossierResponse dossierResponse(StatutKyc statut) {
        return new KycDossierResponse(
                1L, DOSSIER_UID, "CLI-001", "Kouam", "Jean-Pierre",
                NiveauKyc.NIVEAU_1, statut, NiveauRisque.BAS, false,
                null, null, null);
    }

    // ── POST /kyc/dossiers ────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /kyc/dossiers — initier un dossier")
    class InitierDossier {

        @Test
        @DisplayName("→ 201 CREATED quand la requête est valide")
        void initier_valide_retourne_201() throws Exception {
            when(kycService.initierDossier(any(), any()))
                    .thenReturn(dossierResponse(StatutKyc.INITIE));

            mockMvc.perform(post("/kyc/dossiers")
                            .with(TestHelper.asDsi())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(initierRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.statut").value("INITIE"))
                    .andExpect(jsonPath("$.data.clientId").value("CLI-001"));
        }

        @Test
        @DisplayName("→ 409 CONFLICT si dossier déjà existant pour ce client")
        void initier_doublon_retourne_409() throws Exception {
            when(kycService.initierDossier(any(), any()))
                    .thenThrow(new ResponseStatusException(CONFLICT,
                            "Un dossier KYC existe déjà pour le client CLI-001"));

            mockMvc.perform(post("/kyc/dossiers")
                            .with(TestHelper.asDsi())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(initierRequest())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("→ 400 si clientId manquant (contrainte @NotBlank)")
        void initier_clientId_manquant_retourne_400() throws Exception {
            String invalid = """
                    {"clientId":"","nomClient":"Kouam",
                     "niveauDemande":"NIVEAU_1","estPep":false}
                    """;
            mockMvc.perform(post("/kyc/dossiers")
                            .with(TestHelper.asDsi())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("→ 401 si non authentifié")
        void initier_non_authentifie_retourne_401() throws Exception {
            mockMvc.perform(post("/kyc/dossiers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(initierRequest())))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /kyc/dossiers ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /kyc/dossiers — liste paginée")
    class ListDossiers {

        @Test
        @DisplayName("→ 200 avec contenu paginé pour DSI")
        void list_dsi_retourne_200() throws Exception {
            PageResponse<KycDossierResponse> page =
                    PageResponse.of(List.of(dossierResponse(StatutKyc.EN_COURS)), 0, 20, 1L);
            when(kycService.listDossiers(anyLong(), any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(page);

            mockMvc.perform(get("/kyc/dossiers")
                            .with(TestHelper.asDsi())
                            .param("page", "0").param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.first").value(true))
                    .andExpect(jsonPath("$.data.last").value(true));
        }

        @Test
        @DisplayName("→ 200 avec filtre statut=APPROUVE")
        void list_filtre_statut() throws Exception {
            PageResponse<KycDossierResponse> page =
                    PageResponse.of(List.of(dossierResponse(StatutKyc.APPROUVE)), 0, 20, 1L);
            when(kycService.listDossiers(eq(1L), eq(StatutKyc.APPROUVE), isNull(), isNull(), eq(0), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/kyc/dossiers")
                            .with(TestHelper.asDsi())
                            .param("statut", "APPROUVE"))
                    .andExpect(status().isOk());

            verify(kycService).listDossiers(1L, StatutKyc.APPROUVE, null, null, 0, 20);
        }

        @Test
        @DisplayName("→ 403 si ANALYSTE (pas de role approprié)")
        void list_analyste_retourne_403() throws Exception {
            mockMvc.perform(get("/kyc/dossiers").with(TestHelper.asAnalyste()))
                    .andExpect(status().isForbidden());
        }
    }

    // ── GET /kyc/dossiers/{uid} ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /kyc/dossiers/{uid} → 200 avec le détail du dossier")
    void getDossier_existant_retourne_200() throws Exception {
        when(kycService.getDossier(DOSSIER_UID))
                .thenReturn(dossierResponse(StatutKyc.EN_COURS));

        mockMvc.perform(get("/kyc/dossiers/{uid}", DOSSIER_UID)
                        .with(TestHelper.asDsi()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uid").value(DOSSIER_UID.toString()));
    }

    @Test
    @DisplayName("GET /kyc/dossiers/{uid} → 404 si uid inconnu")
    void getDossier_inconnu_retourne_404() throws Exception {
        when(kycService.getDossier(any()))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Dossier introuvable"));

        mockMvc.perform(get("/kyc/dossiers/{uid}", UUID.randomUUID())
                        .with(TestHelper.asDsi()))
                .andExpect(status().isNotFound());
    }

    // ── PUT /kyc/dossiers/{uid}/risque ────────────────────────────────────────

    @Test
    @DisplayName("PUT /kyc/dossiers/{uid}/risque → 200 après évaluation PPE ELEVE")
    void evaluerRisque_ppe_retourne_200() throws Exception {
        KycDossierResponse resp = new KycDossierResponse(
                1L, DOSSIER_UID, "CLI-001", "Kouam", "Jean-Pierre",
                NiveauKyc.NIVEAU_3, StatutKyc.EN_COURS, NiveauRisque.ELEVE, true,
                null, null, null);
        when(kycService.evaluerRisque(eq(DOSSIER_UID), any(), any())).thenReturn(resp);

        String body = """
                {"estPep":true,"verifSanctions":true,"verifListesNoires":false,
                 "niveauRisqueManuel":"ELEVE"}
                """;

        mockMvc.perform(put("/kyc/dossiers/{uid}/risque", DOSSIER_UID)
                        .with(TestHelper.asDsi())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.niveauRisque").value("ELEVE"))
                .andExpect(jsonPath("$.data.estPep").value(true));
    }

    // ── PUT /kyc/dossiers/{uid}/verification ──────────────────────────────────

    @Test
    @DisplayName("PUT /kyc/dossiers/{uid}/verification → 200 avec décision APPROUVE")
    void verifier_approuve_retourne_200() throws Exception {
        when(kycService.verifier(eq(DOSSIER_UID), any(), any()))
                .thenReturn(dossierResponse(StatutKyc.APPROUVE));

        String body = """
                {"decision":"APPROUVE","motif":"Documents conformes"}
                """;

        mockMvc.perform(put("/kyc/dossiers/{uid}/verification", DOSSIER_UID)
                        .with(TestHelper.asDsi())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("APPROUVE"));
    }

    @Test
    @DisplayName("PUT /kyc/dossiers/{uid}/verification → 200 avec décision REJETE")
    void verifier_rejete_retourne_200() throws Exception {
        when(kycService.verifier(eq(DOSSIER_UID), any(), any()))
                .thenReturn(dossierResponse(StatutKyc.REJETE));

        String body = """
                {"decision":"REJETE","motif":"Pièce expirée"}
                """;

        mockMvc.perform(put("/kyc/dossiers/{uid}/verification", DOSSIER_UID)
                        .with(TestHelper.asDsi())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statut").value("REJETE"));
    }
}
