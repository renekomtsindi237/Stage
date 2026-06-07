package cm.imf.pipeline.controller;

import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.service.IUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(TestSecurityConfig.class)
@DisplayName("UserController — tests MockMvc")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean IUserService userService;

    private User buildUser() {
        return User.builder()
                .id(1L)
                .username("agent01")
                .passwordHash("hash")
                .role(Role.AGENT)
                .actif(true)
                .build();
    }

    @Test
    @DisplayName("GET /api/users/me — retourne le profil de l'utilisateur connecté")
    void getMe_200() throws Exception {
        User user = buildUser();

        mockMvc.perform(get("/users/me").with(user(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("agent01"))
                .andExpect(jsonPath("$.data.role").value("AGENT"));
    }

    @Test
    @DisplayName("POST /api/users/me/fcm-token — enregistre le token FCM")
    void updateFcmToken_200() throws Exception {
        User user = buildUser();
        doNothing().when(userService).updateFcmToken(any(), eq("token-firebase-xyz"));

        String body = """
                {"token":"token-firebase-xyz"}
                """;

        mockMvc.perform(post("/users/me/fcm-token")
                        .with(user(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Token FCM enregistré"));
    }
}
