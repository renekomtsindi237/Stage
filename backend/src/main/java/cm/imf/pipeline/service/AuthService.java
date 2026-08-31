package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    /** Session agent mobile : 24 h à partir de l'horodatage OTP (Africa/Douala). */
    static final Duration AGENT_MOBILE_SESSION = Duration.ofHours(24);
    static final ZoneId WORK_ZONE = ZoneId.of("Africa/Douala");

    // ── Login direct SUPER_ADMIN et SUPPORT (email + mot de passe, sans OTP) ──

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Résoudre l'email → username avant d'appeler l'AuthenticationManager
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Identifiants invalides"));

        if (user.getRole() != Role.SUPER_ADMIN && user.getRole() != Role.SUPPORT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ce compte utilise l'authentification par OTP. Utilisez /auth/request-otp.");
        }

        // AuthenticationManager utilise le username interne pour valider le mot de passe via BCrypt
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), request.password()));

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
        if (isAgentMobileSessionExpired(stored, user)) {
            refreshTokenRepository.delete(stored);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Session expirée. Reconnectez-vous.");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);

        return new AuthResponse(
                newAccessToken,
                request.refreshToken(),
                user.getRole().name(),
                user.getUsername(),
                user.getImf() != null && user.getImf().getUid() != null ? user.getImf().getUid().toString() : null,
                user.getImf() != null ? user.getImf().getCode() : null,
                user.getImf() != null ? user.getImf().getNom()  : null,
                user.getImf() != null ? user.getImf().getLogoUrl() : null,
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

        if (user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.SUPPORT) {
            log.warn("Tentative OTP sur compte admin ({}) — ignorée", user.getRole());
            return new OtpInitResponse(MSG);
        }

        // Invalider les OTP précédents non utilisés
        otpCodeRepository.deleteByUser(user);

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

        otpCodeRepository.save(OtpCode.builder()
                .user(user)
                .codeHash(hash(code))
                .expiresAt(OffsetDateTime.now().plusMinutes(10))
                .attemptsUsed((short) 0)
                .used(false)
                .build());

        emailService.sendOtpEmail(user.getEmail(), user.getUsername(), code);
        log.info("OTP mis en file d'envoi pour : {} — l'accusé d'envoi SMTP est asynchrone", user.getUsername());

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
            otp.setAttemptsUsed((short) (otp.getAttemptsUsed() + 1));
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

        // Première connexion : activer le compte directement — pas de mot de passe requis
        if (user.isMustChangePassword()) {
            user.setMustChangePassword(false);
            userRepository.save(user);
            log.info("Compte activé à la première connexion OTP : {}", user.getUsername());
        }

        userRepository.updateLastLogin(user.getId(), OffsetDateTime.now(WORK_ZONE));
        AuthResponse tokens = issueTokens(user);
        OffsetDateTime sessionExpiresAt = user.getRole() == Role.AGENT
                ? OffsetDateTime.now(WORK_ZONE).plus(AGENT_MOBILE_SESSION)
                : null;
        return OtpVerifyResponse.authenticated(tokens, sessionExpiresAt);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse issueTokens(User user) {
        String accessToken        = jwtTokenProvider.generateAccessToken(user);
        String refreshTokenValue  = jwtTokenProvider.generateRefreshTokenValue();

        refreshTokenRepository.deleteByUser(user);

        long refreshMs = user.getRole() == Role.AGENT
                ? AGENT_MOBILE_SESSION.toMillis()
                : jwtTokenProvider.getRefreshTokenExpiryMs();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(refreshTokenValue))
                .expiresAt(OffsetDateTime.now(WORK_ZONE).plusSeconds(refreshMs / 1000))
                .build());

        return new AuthResponse(
                accessToken,
                refreshTokenValue,
                user.getRole().name(),
                user.getUsername(),
                user.getImf() != null && user.getImf().getUid() != null ? user.getImf().getUid().toString() : null,
                user.getImf() != null ? user.getImf().getCode() : null,
                user.getImf() != null ? user.getImf().getNom()  : null,
                user.getImf() != null ? user.getImf().getLogoUrl() : null,
                user.isMustChangePassword(),
                jwtTokenProvider.getAccessTokenExpiryMs()
        );
    }

    private boolean isAgentMobileSessionExpired(RefreshToken stored, User user) {
        if (user.getRole() != Role.AGENT) return false;
        OffsetDateTime start = stored.getCreatedAt();
        if (start == null) {
            start = stored.getExpiresAt() != null
                    ? stored.getExpiresAt().minus(AGENT_MOBILE_SESSION)
                    : OffsetDateTime.now(WORK_ZONE).minus(AGENT_MOBILE_SESSION);
        }
        return OffsetDateTime.now(WORK_ZONE).isAfter(start.plus(AGENT_MOBILE_SESSION));
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
