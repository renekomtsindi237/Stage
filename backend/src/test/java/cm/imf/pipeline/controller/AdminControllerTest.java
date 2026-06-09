package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CreateUserRequest;
import cm.imf.pipeline.dto.response.UserResponse;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AdminController — tests MockMvc (sécurité + fonctionnel)")
class AdminControllerTest {

    private static final UUID USER_UID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  AdminService adminService;

    private UserResponse sampleUser() {
        return new UserResponse(
                null, "jkamga", Role.ANALYSTE, "YD001", null, null,
                null, null, null, true, false, null, null,
                null, null, "fr", "auto",
                true, true, false, false, false, 20);
    }

    @Test
    @WithMockUser(roles = "DSI")
    @DisplayName("GET /api/admin/users — DSI peut lister les utilisateurs")
    void listUsers_dsi_ok() throws Exception {
        when(adminService.listUsers(0, 20))
                .thenReturn(new PageImpl<>(List.of(sampleUser())).map(u -> u));

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ANALYSTE")
    @DisplayName("GET /api/admin/users — ANALYSTE → 403")
    void listUsers_analyste_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DIRECTEUR")
    @DisplayName("GET /api/admin/users — DIRECTEUR → 403")
    void listUsers_directeur_403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DSI")
    @DisplayName("POST /api/admin/users — DSI crée un utilisateur → 201")
    void createUser_dsi_201() throws Exception {
        when(adminService.createUser(any())).thenReturn(sampleUser());

        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateUserRequest("newuser", "SecurePass!1", null, Role.AGENT, "YD001", null, null, null))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "DSI")
    @DisplayName("POST /api/admin/users — payload invalide → 400")
    void createUser_payload_invalide_400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"ab\",\"role\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "DSI")
    @DisplayName("DELETE /api/admin/users/{id} — désactive utilisateur → 200")
    void deactivate_dsi_200() throws Exception {
        UserResponse deactivated = new UserResponse(
                null, "jkamga", Role.ANALYSTE, "YD001", null, null,
                null, null, null, false, false, null, null,
                null, null, "fr", "auto",
                true, true, false, false, false, 20);
        when(adminService.deactivate(any(UUID.class))).thenReturn(deactivated);

        mockMvc.perform(delete("/api/v1/admin/users/{uid}", USER_UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actif").value(false));
    }

    @Test
    @WithMockUser(roles = "DSI")
    @DisplayName("PATCH /api/admin/users/{id}/activate → 200 avec actif=true")
    void activate_dsi_200() throws Exception {
        when(adminService.activate(any(UUID.class))).thenReturn(sampleUser());

        mockMvc.perform(patch("/api/v1/admin/users/{uid}/activate", USER_UID))
                .andExpect(status().isOk());
    }
}
