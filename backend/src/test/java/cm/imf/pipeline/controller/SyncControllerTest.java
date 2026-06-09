package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CollecteRequest;
import cm.imf.pipeline.dto.request.SyncRequest;
import cm.imf.pipeline.dto.response.SyncItemResult;
import cm.imf.pipeline.dto.response.SyncResponse;
import cm.imf.pipeline.dto.response.SyncStatusResponse;
import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.service.CollecteSyncService;
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

@WebMvcTest(SyncController.class)
@Import(TestSecurityConfig.class)
@DisplayName("SyncController — tests MockMvc")
class SyncControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  CollecteSyncService syncService;

    private SyncRequest validRequest() {
        CollecteRequest item = new CollecteRequest(
                "MOB-001", "CLI001", "PRE001", LocalDate.now(),
                new BigDecimal("25000"), CanalPaiement.MTN, "REF001",
                null, null, null);
        return new SyncRequest(UUID.randomUUID().toString(),
                "DEVICE-001", OffsetDateTime.now(), List.of(item));
    }

    private SyncResponse successResponse(String syncId) {
        List<SyncItemResult> resultats = List.of(
                SyncItemResult.succes("MOB-001", 1L, "Collecte enregistrée avec succès."));
        return SyncResponse.of(syncId, resultats,
                "Synchronisation complète : toutes les collectes ont été enregistrées avec succès.");
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /api/sync/collectes — AGENT soumet batch valide → 200 avec résultats")
    void syncCollectes_agent_batch_valide_200() throws Exception {
        SyncRequest req = validRequest();
        when(syncService.processSync(any(), any(), any()))
                .thenReturn(successResponse(req.syncId()));

        mockMvc.perform(post("/api/v1/sync/collectes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statutGlobal").value("COMPLETE"))
                .andExpect(jsonPath("$.data.stats.succes").value(1))
                .andExpect(jsonPath("$.data.resultats[0].code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.resultats[0].messageUtilisateur")
                        .value("Collecte enregistrée avec succès."));
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("POST /api/sync/collectes — ANALYSTE → 403 FORBIDDEN")
    void syncCollectes_analyste_403() throws Exception {
        mockMvc.perform(post("/api/v1/sync/collectes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /api/sync/collectes — payload invalide (liste vide) → 400")
    void syncCollectes_liste_vide_400() throws Exception {
        SyncRequest emptyReq = new SyncRequest(
                UUID.randomUUID().toString(), "DEVICE-001",
                OffsetDateTime.now(), List.of());

        mockMvc.perform(post("/api/v1/sync/collectes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /api/sync/collectes — syncId manquant → 400")
    void syncCollectes_syncid_manquant_400() throws Exception {
        mockMvc.perform(post("/api/v1/sync/collectes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"D1\",\"clientSyncTimestamp\":\"2024-01-01T10:00:00Z\"," +
                                "\"items\":[{\"idCollecteMobile\":\"M1\",\"clientId\":\"C1\"," +
                                "\"pretId\":\"P1\",\"dateCollecte\":\"2024-01-01\"," +
                                "\"montantCollecte\":25000,\"canalPaiement\":\"MTN\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("POST /api/sync/collectes — sync partielle → 200 avec details conflits")
    void syncCollectes_partielle_avec_conflits() throws Exception {
        SyncRequest req = validRequest();
        List<SyncItemResult> resultats = List.of(
                SyncItemResult.succes("MOB-001", 1L, "Collecte enregistrée avec succès."),
                SyncItemResult.conflit("MOB-002",
                        "Doublon détecté : référence de transaction déjà enregistrée."));
        SyncResponse partial = SyncResponse.of(req.syncId(), resultats,
                "Synchronisation partielle : 1/2 collecte(s) enregistrée(s).");

        when(syncService.processSync(any(), any(), any())).thenReturn(partial);

        mockMvc.perform(post("/api/v1/sync/collectes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statutGlobal").value("PARTIELLE"))
                .andExpect(jsonPath("$.data.stats.conflits").value(1))
                .andExpect(jsonPath("$.data.resultats[1].code").value("CONFLIT"))
                .andExpect(jsonPath("$.data.resultats[1].messageUtilisateur")
                        .value("Doublon détecté : référence de transaction déjà enregistrée."));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    @DisplayName("GET /api/sync/status/{deviceId} — retourne statut de l'appareil")
    void getSyncStatus_ok() throws Exception {
        SyncStatusResponse status = new SyncStatusResponse(
                "DEVICE-001", OffsetDateTime.now(), 5, 47, 0,
                "Toutes les collectes sont synchronisées.");
        when(syncService.getSyncStatus("DEVICE-001")).thenReturn(status);

        mockMvc.perform(get("/api/v1/sync/status/DEVICE-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceId").value("DEVICE-001"))
                .andExpect(jsonPath("$.data.nbSyncTotal").value(5))
                .andExpect(jsonPath("$.data.nbCollectesConfirmees").value(47))
                .andExpect(jsonPath("$.data.message").value("Toutes les collectes sont synchronisées."));
    }

    @Test
    @DisplayName("POST /api/sync/collectes — non authentifié → 401")
    void syncCollectes_non_authentifie_401() throws Exception {
        mockMvc.perform(post("/api/v1/sync/collectes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }
}
