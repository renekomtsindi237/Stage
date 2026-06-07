package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.entity.RefreshToken;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.repository.RefreshTokenRepository;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = (User) auth.getPrincipal();
        userRepository.updateLastLogin(user.getId(), OffsetDateTime.now());

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshTokenValue = jwtTokenProvider.generateRefreshTokenValue();

        // Supprimer les anciens refresh tokens de cet utilisateur
        refreshTokenRepository.deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(refreshTokenValue))
                .expiresAt(OffsetDateTime.now().plusSeconds(
                        jwtTokenProvider.getRefreshTokenExpiryMs() / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        log.info("Connexion réussie pour l'utilisateur : {}", user.getUsername());
        return new AuthResponse(
                accessToken,
                refreshTokenValue,
                user.getRole().name(),
                user.getUsername(),
                user.getImf() != null && user.getImf().getUid() != null ? user.getImf().getUid().toString() : null,
                user.getImf() != null ? user.getImf().getCode() : null,
                user.getImf() != null ? user.getImf().getNom()  : null,
                user.isMustChangePassword(),
                jwtTokenProvider.getAccessTokenExpiryMs()
        );
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = hash(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expiré");
        }

        User user = stored.getUser();
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        return new AuthResponse(
                newAccessToken,
                request.refreshToken(),
                user.getRole().name(),
                user.getUsername(),
                user.getImf() != null && user.getImf().getUid() != null ? user.getImf().getUid().toString() : null,
                user.getImf() != null ? user.getImf().getCode() : null,
                user.getImf() != null ? user.getImf().getNom()  : null,
                user.isMustChangePassword(),
                jwtTokenProvider.getAccessTokenExpiryMs()
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByTokenHash(hash(refreshToken));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}
