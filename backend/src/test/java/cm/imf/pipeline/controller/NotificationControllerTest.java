package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.NotificationDto;
import cm.imf.pipeline.service.INotifPersistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(TestSecurityConfig.class)
@DisplayName("NotificationController — historique notifications")
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  INotifPersistService notifService;

    private NotificationDto sampleNotif() {
        return new NotificationDto(
                UUID.randomUUID().toString(), "ALERTE_IMPAYE",
                "Alerte impayé", "Le client CLI-001 est en retard de 30 jours",
                null, false, OffsetDateTime.now());
    }

    // ── GET /notifications ────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /notifications → 200 avec page de notifications")
    void list_retourne_200() throws Exception {
        when(notifService.getNotifications(anyLong(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(sampleNotif())));

        mockMvc.perform(get("/notifications").with(TestHelper.asAnalyste()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].type").value("ALERTE_IMPAYE"));
    }

    @Test
    @DisplayName("GET /notifications → 401 si non authentifié")
    void list_non_authentifie_retourne_401() throws Exception {
        mockMvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /notifications/unread-count ──────────────────────────────────────

    @Test
    @DisplayName("GET /notifications/unread-count → 200 avec compteur")
    void unreadCount_retourne_200() throws Exception {
        when(notifService.countUnread(anyLong(), anyString())).thenReturn(5L);

        mockMvc.perform(get("/notifications/unread-count")
                        .with(TestHelper.asAnalyste()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(5));
    }

    // ── PUT /notifications/{uid}/read ─────────────────────────────────────────

    @Test
    @DisplayName("PUT /notifications/{uid}/read → 200, notification marquée lue")
    void markRead_retourne_200() throws Exception {
        UUID uid = UUID.randomUUID();
        doNothing().when(notifService).markAsRead(uid);

        mockMvc.perform(put("/notifications/{uid}/read", uid)
                        .with(TestHelper.asAnalyste()))
                .andExpect(status().isOk());

        verify(notifService).markAsRead(uid);
    }

    // ── PUT /notifications/read-all ───────────────────────────────────────────

    @Test
    @DisplayName("PUT /notifications/read-all → 200, toutes marquées lues")
    void markAllRead_retourne_200() throws Exception {
        doNothing().when(notifService).markAllAsRead(anyLong(), anyString());

        mockMvc.perform(put("/notifications/read-all")
                        .with(TestHelper.asAnalyste()))
                .andExpect(status().isOk());
    }
}
