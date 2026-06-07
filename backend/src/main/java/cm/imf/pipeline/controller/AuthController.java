package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.LoginRequest;
import cm.imf.pipeline.dto.request.RefreshRequest;
import cm.imf.pipeline.dto.response.AuthResponse;
import cm.imf.pipeline.entity.AuditTrail;
import cm.imf.pipeline.security.Auditable;
import cm.imf.pipeline.service.IAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
@Tag(name = "Authentification", description = "Login, refresh et déconnexion JWT")
public class AuthController {

    private static final String ACCESS_COOKIE  = "imf_access";
    private static final String REFRESH_COOKIE = "imf_refresh";

    private final IAuthService authService;

    @Value("${app.security.cookie-secure:true}")
    private boolean cookieSecure;

    // ── Endpoints ────────────────────────────────────────────────────────────

    @Operation(summary = "Connexion — retourne accessToken + refreshToken (body pour mobile, cookies pour web)")
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

    @Operation(summary = "Rafraîchir l'accessToken — lit le refresh depuis le cookie (web) ou le body (mobile)")
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

        // Access token : durée de vie = durée du token JWT (15 min par défaut)
        ResponseCookie access = ResponseCookie.from(ACCESS_COOKIE, accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api")
                .maxAge(accessExpiresInMs / 1000)
                .build();

        // Refresh token : scopé uniquement sur /api/auth/refresh pour minimiser l'exposition
        ResponseCookie refresh = ResponseCookie.from(REFRESH_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth/")
                .maxAge(7L * 24 * 3600)  // 7 jours
                .build();

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
        // Priorité 1 : cookie httpOnly (clients web Angular)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (REFRESH_COOKIE.equals(c.getName()) && c.getValue() != null
                        && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
        }
        // Priorité 2 : body JSON (clients mobiles Flutter / API externe)
        return (body != null) ? body.refreshToken() : null;
    }
}
