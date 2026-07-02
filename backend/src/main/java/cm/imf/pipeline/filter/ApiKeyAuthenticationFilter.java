package cm.imf.pipeline.filter;

import cm.imf.pipeline.entity.ApiClient;
import cm.imf.pipeline.repository.ApiClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * Filtre d'authentification par clé API pour les intégrations externes (CBS, BluCash).
 *
 * Protocole :
 *   1. Lire le header X-Api-Key
 *   2. Extraire le préfixe (17 premiers chars) pour la recherche DB
 *   3. Vérifier le hash SHA-256 de la clé complète
 *   4. Injecter le systemUser de l'ApiClient dans le SecurityContext
 *
 * Seuls les endpoints /api/v1/external/** sont concernés — le filtre ignore les autres.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Api-Key";
    public static final int    PREFIX_LENGTH  = 17; // "mcr_live_" (9) + 8 hex chars

    private final ApiClientRepository apiClientRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain) throws ServletException, IOException {

        // Traiter uniquement les routes external/**
        if (!request.getRequestURI().contains("/external/")) {
            chain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            rejectUnauthorized(response, "Header X-Api-Key manquant");
            return;
        }

        if (rawKey.length() < PREFIX_LENGTH) {
            rejectUnauthorized(response, "Clé API invalide");
            return;
        }

        String prefix = rawKey.substring(0, PREFIX_LENGTH);
        ApiClient client = apiClientRepository.findByKeyPrefix(prefix).orElse(null);

        if (client == null || !client.isActive()) {
            rejectUnauthorized(response, "Clé API invalide ou révoquée");
            return;
        }

        String hashProvided = sha256hex(rawKey);
        if (!hashProvided.equals(client.getKeyHash())) {
            log.warn("Tentative d'accès API avec clé incorrecte — préfixe {} depuis {}",
                    prefix, request.getRemoteAddr());
            rejectUnauthorized(response, "Clé API invalide");
            return;
        }

        // Clé valide — injecter le systemUser dans le SecurityContext
        if (client.getSystemUser() != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    client.getSystemUser(),
                    null,
                    client.getSystemUser().getAuthorities()
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            // Mettre à jour last_used_at en arrière-plan (best-effort)
            try {
                apiClientRepository.updateLastUsedAt(client.getId(), OffsetDateTime.now());
            } catch (Exception e) {
                log.debug("Mise à jour last_used_at ignorée : {}", e.getMessage());
            }
        }

        chain.doFilter(request, response);
    }

    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\"}");
    }

    public static String sha256hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}
