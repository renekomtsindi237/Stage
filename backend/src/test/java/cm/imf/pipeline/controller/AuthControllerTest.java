package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AuthController — tests MockMvc")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  AuthService authService;

    @Test
    @DisplayName("POST /api/auth/login — identifiants valides → 200 + accessToken")
    void login_valide_retourne_200() throws Exception {
        AuthResponse response = new AuthResponse("access_token", "refresh_token",
                "ANALYSTE", "jkamga", 900L);
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("jkamga", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_token"))
                .andExpect(jsonPath("$.role").value("ANALYSTE"))
                .andExpect(jsonPath("$.username").value("jkamga"));
    }

    @Test
    @DisplayName("POST /api/auth/login — mauvais identifiants → 401")
    void login_invalide_retourne_401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("jkamga", "wrongpass"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login — champs vides → 400 validation error")
    void login_champs_vides_retourne_400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/refresh — token valide → 200 + nouveau accessToken")
    void refresh_token_valide() throws Exception {
        AuthResponse response = new AuthResponse("new_access", "same_refresh",
                "ANALYSTE", "jkamga", 900L);
        when(authService.refresh(any())).thenReturn(response);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest("valid_refresh_token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new_access"));
    }

    @Test
    @DisplayName("POST /api/auth/logout — retourne 204 NO CONTENT")
    void logout_retourne_204() throws Exception {
        doNothing().when(authService).logout(any());

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest("some_refresh_token"))))
                .andExpect(status().isNoContent());
    }
}
