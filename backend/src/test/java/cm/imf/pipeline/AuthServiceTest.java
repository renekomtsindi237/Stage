package cm.imf.pipeline;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.entity.RefreshToken;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.RefreshTokenRepository;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.security.JwtTokenProvider;
import cm.imf.pipeline.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;

    @InjectMocks AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("jkamga")
                .passwordHash("$2a$12$hash")
                .role(Role.ANALYSTE)
                .actif(true)
                .build();
    }

    @Test
    void login_retourne_authResponse_valide() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(testUser, null,
                        testUser.getAuthorities()));
        when(jwtTokenProvider.generateAccessToken(testUser)).thenReturn("access_token_xxx");
        when(jwtTokenProvider.generateRefreshTokenValue()).thenReturn("refresh_token_yyy");
        when(jwtTokenProvider.getRefreshTokenExpiryMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("jkamga", "password"));

        assertThat(response.accessToken()).isEqualTo("access_token_xxx");
        assertThat(response.role()).isEqualTo("ANALYSTE");
        assertThat(response.username()).isEqualTo("jkamga");
        verify(refreshTokenRepository).deleteByUser(testUser);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
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
