package cm.imf.pipeline.controller;

import cm.imf.pipeline.repository.AlerteRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InternalController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("InternalController — API interne pipeline Python")
class InternalControllerTest {

    private static final String VALID_KEY   = "test_internal_api_key";
    private static final String INVALID_KEY = "wrong_key";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AlerteRepository        alerteRepository;
    @MockBean INotificationService    notificationService;
    @MockBean SseEmitterRegistry      sseRegistry;

    // ── POST /internal/alertes ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /internal/alertes — clé valide → 201 avec id_pret")
    void creerAlerte_cle_valide_201() throws Exception {
        when(alerteRepository.findByIdPretAndStatutAlerte(anyString(), any()))
                .thenReturn(Optional.empty());
        when(alerteRepository.save(any())).thenAnswer(inv -> {
            var a = inv.<cm.imf.pipeline.entity.AlerteImpaye>getArgument(0);
            // simuler l'ID généré
            var saved = cm.imf.pipeline.entity.AlerteImpaye.builder()
                    .idPret(a.getIdPret())
                    .joursRetard(a.getJoursRetard())
                    .montantEnRetard(a.getMontantEnRetard())
                    .statutAlerte(a.getStatutAlerte())
                    .dateGeneration(a.getDateGeneration())
                    .build();
            try {
                var f = saved.getClass().getSuperclass().getDeclaredField("id");
                f.setAccessible(true);
                f.set(saved, 42L);
            } catch (Exception ignored) { /* Lombok @Data sets id via builder */ }
            return saved;
        });
        doNothing().when(notificationService).notifierAlerteImpaye(anyLong());

        Map<String, Object> payload = Map.of(
                "id_pret", "PRE-001",
                "jours_retard", 45,
                "montant_en_retard", "250000.00"
        );

        mockMvc.perform(post("/api/v1/internal/alertes")
                        .header("X-Internal-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id_pret").value("PRE-001"));
    }

    @Test
    @DisplayName("POST /internal/alertes — clé invalide → 403")
    void creerAlerte_cle_invalide_403() throws Exception {
        Map<String, Object> payload = Map.of(
                "id_pret", "PRE-001",
                "jours_retard", 45,
                "montant_en_retard", "250000.00"
        );

        mockMvc.perform(post("/api/v1/internal/alertes")
                        .header("X-Internal-Api-Key", INVALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /internal/alertes — alerte déjà active → 409 CONFLICT")
    void creerAlerte_doublonnee_409() throws Exception {
        var existante = cm.imf.pipeline.entity.AlerteImpaye.builder()
                .idPret("PRE-001")
                .joursRetard(30)
                .montantEnRetard(java.math.BigDecimal.TEN)
                .statutAlerte(cm.imf.pipeline.enums.StatutAlerte.ACTIVE)
                .dateGeneration(java.time.OffsetDateTime.now())
                .build();
        when(alerteRepository.findByIdPretAndStatutAlerte(anyString(), any()))
                .thenReturn(Optional.of(existante));

        Map<String, Object> payload = Map.of(
                "id_pret", "PRE-001",
                "jours_retard", 50,
                "montant_en_retard", "300000"
        );

        mockMvc.perform(post("/api/v1/internal/alertes")
                        .header("X-Internal-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /internal/alertes — header manquant → 400")
    void creerAlerte_sans_header_400() throws Exception {
        Map<String, Object> payload = Map.of(
                "id_pret", "PRE-001",
                "jours_retard", 45,
                "montant_en_retard", "100000"
        );

        mockMvc.perform(post("/api/v1/internal/alertes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /internal/fcm-token ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /internal/fcm-token — clé valide → 204")
    void registerFcm_cle_valide_204() throws Exception {
        doNothing().when(notificationService).registerFcmToken(anyLong(), anyString());

        Map<String, Object> payload = Map.of("user_id", 1, "fcm_token", "firebase-token-abc");

        mockMvc.perform(post("/api/v1/internal/fcm-token")
                        .header("X-Internal-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /internal/fcm-token — clé invalide → 403")
    void registerFcm_cle_invalide_403() throws Exception {
        Map<String, Object> payload = Map.of("user_id", 1, "fcm_token", "token");

        mockMvc.perform(post("/api/v1/internal/fcm-token")
                        .header("X-Internal-Api-Key", INVALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isForbidden());
    }
}
