package cm.imf.pipeline.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            @Value("${jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    public String generateAccessToken(UserDetails userDetails) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("role", userDetails.getAuthorities().iterator().next().getAuthority())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiryMs));

        // Ajout de l'imfId dans le token pour traçabilité côté client
        if (userDetails instanceof cm.imf.pipeline.entity.User u && u.getImf() != null) {
            builder.claim("imfId",   u.getImf().getId())
                   .claim("imfCode", u.getImf().getCode());
        }

        return builder.signWith(secretKey).compact();
    }

    public String generateRefreshTokenValue() {
        return UUID.randomUUID().toString();
    }

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expiré : {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT invalide : {}", e.getMessage());
        }
        return false;
    }

    // ── Password reset token (15 min, claim type=PASSWORD_RESET) ─────────────

    private static final String RESET_TOKEN_TYPE = "PASSWORD_RESET";
    private static final long   RESET_TOKEN_EXPIRY_MS = 15 * 60 * 1000L;

    public String generatePasswordResetToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("type", RESET_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + RESET_TOKEN_EXPIRY_MS))
                .signWith(secretKey)
                .compact();
    }

    public long getResetTokenExpirySeconds() {
        return RESET_TOKEN_EXPIRY_MS / 1000;
    }

    public String validatePasswordResetToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!RESET_TOKEN_TYPE.equals(claims.get("type", String.class))) {
                throw new IllegalArgumentException("Token de type incorrect");
            }
            return claims.getSubject();
        } catch (ExpiredJwtException e) {
            throw new IllegalArgumentException("Token de réinitialisation expiré");
        } catch (JwtException e) {
            throw new IllegalArgumentException("Token de réinitialisation invalide");
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
