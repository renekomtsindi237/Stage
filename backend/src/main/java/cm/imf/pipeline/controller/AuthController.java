package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.request.ResetPasswordWithTokenRequest;
import cm.imf.pipeline.dto.request.VerifyOtpRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.dto.response.OtpInitResponse;
import cm.imf.pipeline.dto.response.OtpVerifyResponse;
import cm.imf.pipeline.entity.AuditTrail;
import cm.imf.pipeline.security.Auditable;
import cm.imf.pipeline.service.IAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Login SUPER_ADMIN (classique) + OTP pour tous les autres rôles")
public class AuthController {

    private static final String ACCESS_COOKIE  = "imf_access";
    private static final String REFRESH_COOKIE = "imf_refresh";

    private final IAuthService authService;

    @Value("${app.security.cookie-secure:true}")
    private boolean cookieSecure;

    // ── SUPER_ADMIN — login classique (username + password) ──────────────────

    @Operation(summary = "Login SUPER_ADMIN uniquement — username + password → tokens. Retourne 403 pour les autres rôles.")
    @PostMapping("/login")
    @Auditable(
        action             = AuditTrail.ACTION_CONNEXION,
        entiteType         = AuditTrail.ENTITE_AUTH,
        entiteIdExpression = "#request.username",
        captureResult      = true
    )
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthResponse auth = authService.login(request);
        setAuthCookies(response, auth.accessToken(), auth.refreshToken(), auth.expiresIn());
        return ResponseEntity.ok(auth);
    }

    // ── OTP — Étape 1 : demander un code par email ────────────────────────────

    @Operation(summary = "Envoie un code OTP à 6 chiffres par email (connexion + activation). Retourne toujours 200.")
    @PostMapping("/request-otp")
    public ResponseEntity<OtpInitResponse> requestOtp(
            @RequestParam @NotBlank @Email String email) {

        return ResponseEntity.ok(authService.requestOtp(email));
    }

    // ── OTP — Étape 2 : vérifier le code ─────────────────────────────────────

    @Operation(summary = """
            Vérifie le code OTP (valable 10 min, max 3 tentatives).
            Réponse status=AUTHENTICATED → cookies + tokens (compte actif).
            Réponse status=MUST_SET_PASSWORD → resetToken JWT 15 min (première connexion).
            """)
    @PostMapping("/verify-otp")
    public ResponseEntity<OtpVerifyResponse> verifyOtp(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletResponse response) {

        OtpVerifyResponse result = authService.verifyOtp(request);

        if (OtpVerifyResponse.AUTHENTICATED.equals(result.status())) {
            setAuthCookies(response, result.accessToken(), result.refreshToken(), result.expiresIn());
        }

        return ResponseEntity.ok(result);
    }

    // ── OTP — Étape 3 : définir le mot de passe fort (activation) ────────────

    @Operation(summary = "Définit le mot de passe fort après vérification OTP (première connexion). Retourne les tokens.")
    @PostMapping("/set-password")
    public ResponseEntity<AuthResponse> setPassword(
            @Valid @RequestBody ResetPasswordWithTokenRequest request,
            HttpServletResponse response) {

        AuthResponse auth = authService.setPasswordWithToken(request);
        setAuthCookies(response, auth.accessToken(), auth.refreshToken(), auth.expiresIn());
        return ResponseEntity.ok(auth);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Operation(summary = "Rafraîchir l'accessToken — cookie (web) ou body (mobile)")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest bodyRequest,
            HttpServletRequest  httpReq,
            HttpServletResponse httpRes) {

        String refreshToken = extractRefreshToken(httpReq, bodyRequest);
        if (refreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Token de rafraîchissement introuvable");
        }

        AuthResponse auth = authService.refresh(new RefreshRequest(refreshToken));
        setAuthCookies(httpRes, auth.accessToken(), auth.refreshToken(), auth.expiresIn());
        return ResponseEntity.ok(auth);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Operation(summary = "Déconnexion — invalide le refreshToken et efface les cookies")
    @PostMapping("/logout")
    @Auditable(
        action             = AuditTrail.ACTION_DECONNEXION,
        entiteType         = AuditTrail.ENTITE_AUTH,
        entiteIdExpression = "#currentUserId"
    )
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshRequest bodyRequest,
            HttpServletRequest  httpReq,
            HttpServletResponse httpRes) {

        String refreshToken = extractRefreshToken(httpReq, bodyRequest);
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearAuthCookies(httpRes);
        return ResponseEntity.noContent().build();
    }

    // ── Cookie helpers ────────────────────────────────────────────────────────

    private void setAuthCookies(HttpServletResponse response,
                                 String accessToken,
                                 String refreshToken,
                                 long   accessExpiresInMs) {

        ResponseCookie access = ResponseCookie.from(ACCESS_COOKIE, accessToken)
                .httpOnly(true).secure(cookieSecure).sameSite("Strict")
                .path("/api").maxAge(accessExpiresInMs / 1000).build();

        ResponseCookie refresh = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true).secure(cookieSecure).sameSite("Strict")
                .path("/api/auth/").maxAge(7L * 24 * 3600).build();

        response.addHeader(HttpHeaders.SET_COOKIE, access.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie access = ResponseCookie.from(ACCESS_COOKIE, "")
                .httpOnly(true).secure(cookieSecure).sameSite("Strict")
                .path("/api").maxAge(0).build();

        ResponseCookie refresh = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(cookieSecure).sameSite("Strict")
                .path("/api/auth/").maxAge(0).build();

        response.addHeader(HttpHeaders.SET_COOKIE, access.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refresh.toString());
    }

    private String extractRefreshToken(HttpServletRequest request, RefreshRequest body) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (REFRESH_COOKIE.equals(c.getName()) && c.getValue() != null
                        && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        return (body != null) ? body.refreshToken() : null;
    }
}
