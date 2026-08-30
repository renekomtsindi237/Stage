package cm.imf.pipeline;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.entity.RefreshToken;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.OtpCodeRepository;
import cm.imf.pipeline.repository.RefreshTokenRepository;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.security.JwtTokenProvider;
import cm.imf.pipeline.service.AuthService;
import cm.imf.pipeline.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager  authenticationManager;
    @Mock JwtTokenProvider       jwtTokenProvider;
    @Mock UserRepository         userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock OtpCodeRepository      otpCodeRepository;
    @Mock EmailService           emailService;
    @Mock PasswordEncoder        passwordEncoder;

    @InjectMocks AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("jkamga")
                .email("jkamga@test.cm")
                .passwordHash("$2a$12$hash")
                .role(Role.SUPER_ADMIN)
                .actif(true)
                .build();
    }

    @Test
    void login_retourne_authResponse_valide() {
        when(userRepository.findByEmail("jkamga@test.cm")).thenReturn(Optional.of(testUser));
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(testUser, null,
                        testUser.getAuthorities()));
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("access_token_xxx");
        when(jwtTokenProvider.generateRefreshTokenValue()).thenReturn("refresh_token_yyy");
        when(jwtTokenProvider.getRefreshTokenExpiryMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("jkamga@test.cm", "password"));

        assertThat(response.accessToken()).isEqualTo("access_token_xxx");
        assertThat(response.role()).isEqualTo("SUPER_ADMIN");
        assertThat(response.username()).isEqualTo("jkamga");
        verify(refreshTokenRepository).deleteByUser(testUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_role_non_super_admin_retourne_403() {
        User analyste = User.builder()
                .username("analyste")
                .email("analyste@test.cm")
                .role(Role.ANALYSTE)
                .actif(true)
                .build();
        when(userRepository.findByEmail("analyste@test.cm")).thenReturn(Optional.of(analyste));

        assertThatThrownBy(() -> authService.login(new LoginRequest("analyste@test.cm", "pass")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("OTP");
    }

    @Test
    void refresh_avec_token_valide_retourne_nouveau_access_token() {
        RefreshToken stored = RefreshToken.builder()
                .user(testUser)
                .tokenHash(hashOf("valid_refresh_token"))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("new_access_token");

        AuthResponse response = authService.refresh(new RefreshRequest("valid_refresh_token"));

        assertThat(response.accessToken()).isEqualTo("new_access_token");
        assertThat(response.username()).isEqualTo("jkamga");
    }

    @Test
    void refresh_avec_token_expiré_leve_exception() {
        RefreshToken expired = RefreshToken.builder()
                .user(testUser)
                .tokenHash(hashOf("expired_token"))
                .expiresAt(OffsetDateTime.now().minusDays(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("expired_token")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expiré");
    }

    @Test
    void refresh_agent_apres_24h_est_refuse() {
        User agent = User.builder()
                .id(2L)
                .username("agent.terrain")
                .email("agent@test.cm")
                .role(Role.AGENT)
                .actif(true)
                .build();
        RefreshToken stored = RefreshToken.builder()
                .user(agent)
                .tokenHash(hashOf("old_refresh"))
                .createdAt(OffsetDateTime.now().minusHours(25))
                .expiresAt(OffsetDateTime.now().plusDays(6))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old_refresh")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Session expirée");
        verify(refreshTokenRepository).delete(stored);
    }

    @Test
    void logout_supprime_le_token() {
        authService.logout("some_refresh_token");
        verify(refreshTokenRepository).deleteByTokenHash(anyString());
    }

    // Helper identique à AuthService.hash()
    private String hashOf(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
