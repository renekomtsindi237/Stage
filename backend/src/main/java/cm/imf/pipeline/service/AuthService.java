package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.request.ResetPasswordWithTokenRequest;
import cm.imf.pipeline.dto.request.VerifyOtpRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.dto.response.OtpInitResponse;
import cm.imf.pipeline.dto.response.OtpVerifyResponse;
import cm.imf.pipeline.entity.OtpCode;
import cm.imf.pipeline.entity.RefreshToken;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.repository.OtpCodeRepository;
import cm.imf.pipeline.repository.RefreshTokenRepository;
import cm.imf.pipeline.repository.UserRepository;
import cm.imf.pipeline.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final AuthenticationManager  authenticationManager;
    private final JwtTokenProvider       jwtTokenProvider;
    private final UserRepository         userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpCodeRepository      otpCodeRepository;
    private final EmailService           emailService;
    private final PasswordEncoder        passwordEncoder;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── Login SUPER_ADMIN (classique — pas d'OTP) ─────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = (User) auth.getPrincipal();

        if (user.getRole() != Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ce compte utilise l'authentification par OTP. Utilisez /auth/request-otp.");
        }

        userRepository.updateLastLogin(user.getId(), OffsetDateTime.now());
        return issueTokens(user);
    }

    // ── Refresh / Logout ──────────────────────────────────────────────────────

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

    // ── OTP — Étape 1 : envoi du code ─────────────────────────────────────────

    @Transactional
    public OtpInitResponse requestOtp(String email) {
        final String MSG = "Si un compte est associé à cet email, un code de vérification a été envoyé.";

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.info("OTP demandé pour email inconnu : {} — réponse générique", email);
            return new OtpInitResponse(MSG);
        }

        User user = userOpt.get();

        if (user.getRole() == Role.SUPER_ADMIN) {
            log.warn("Tentative OTP sur le compte SUPER_ADMIN — ignorée");
            return new OtpInitResponse(MSG);
        }

        // Invalider les OTP précédents non utilisés
        otpCodeRepository.deleteByUser(user);

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        otpCodeRepository.save(OtpCode.builder()
                .user(user)
                .codeHash(hash(code))
                .expiresAt(OffsetDateTime.now().plusMinutes(10))
                .attemptsUsed(0)
                .used(false)
                .build());

        emailService.sendOtpEmail(user.getEmail(), user.getUsername(), code);
        log.info("OTP envoyé à l'utilisateur : {}", user.getUsername());

        return new OtpInitResponse(MSG);
    }

    // ── OTP — Étape 2 : vérification du code ─────────────────────────────────

    @Transactional
    public OtpVerifyResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Code invalide ou expiré"));

        OtpCode otp = otpCodeRepository
                .findTopByUserAndUsedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Code invalide ou expiré"));

        if (otp.isExpired() || otp.isExhausted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code invalide ou expiré");
        }

        if (!otp.getCodeHash().equals(hash(request.code()))) {
            otp.setAttemptsUsed(otp.getAttemptsUsed() + 1);
            if (otp.getAttemptsUsed() >= 3) {
                otp.setUsed(true);
                log.warn("OTP épuisé (3 tentatives) pour : {}", user.getUsername());
            }
            otpCodeRepository.save(otp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code invalide ou expiré");
        }

        otp.setUsed(true);
        otpCodeRepository.save(otp);
        log.info("OTP vérifié pour : {}", user.getUsername());

        if (user.isMustChangePassword()) {
            // Premier login / activation → l'utilisateur doit définir son mot de passe
            String resetToken = jwtTokenProvider.generatePasswordResetToken(user.getUsername());
            return OtpVerifyResponse.mustSetPassword(resetToken, jwtTokenProvider.getResetTokenExpirySeconds());
        }

        // Compte déjà activé → émettre les tokens directement
        userRepository.updateLastLogin(user.getId(), OffsetDateTime.now());
        return OtpVerifyResponse.authenticated(issueTokens(user));
    }

    // ── OTP — Étape 3 : définir le mot de passe (activation) ─────────────────

    @Transactional
    public AuthResponse setPasswordWithToken(ResetPasswordWithTokenRequest request) {
        String username;
        try {
            username = jwtTokenProvider.validatePasswordResetToken(request.resetToken());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Utilisateur introuvable"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Invalider les sessions existantes et OTP résiduels
        refreshTokenRepository.deleteByUser(user);
        otpCodeRepository.deleteByUser(user);

        userRepository.updateLastLogin(user.getId(), OffsetDateTime.now());
        log.info("Mot de passe défini et compte activé pour : {}", username);

        return issueTokens(user);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse issueTokens(User user) {
        String accessToken        = jwtTokenProvider.generateAccessToken(user);
        String refreshTokenValue  = jwtTokenProvider.generateRefreshTokenValue();

        refreshTokenRepository.deleteByUser(user);

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(refreshTokenValue))
                .expiresAt(OffsetDateTime.now().plusSeconds(
                        jwtTokenProvider.getRefreshTokenExpiryMs() / 1000))
                .build());

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
