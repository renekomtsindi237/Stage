package cm.imf.pipeline.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filtre de défense en profondeur pour les endpoints internes (/internal/**).
 * Valide la clé API dans le header X-Internal-Api-Key AVANT que la requête
 * n'atteigne Spring Security ou le DispatcherServlet.
 *
 * Raison d'existence : SecurityConfig laisse passer /internal/** via permitAll()
 * car ces endpoints n'utilisent pas JWT. Ce filtre assure qu'une requête sans
 * clé valide est rejetée avec 403 dès la couche Servlet, sans logguer l'accès
 * comme un appel authentifié.
 *
 * Ordre 0 : exécuté avant RequestLoggingFilter (order 1).
 */
@Slf4j
@Component
@Order(0)
public class InternalApiKeyFilter implements Filter {

    private static final String HEADER_NAME = "X-Internal-Api-Key";

    @Value("${internal.api-key}")
    private String expectedApiKey;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        if (!request.getRequestURI().startsWith("/internal/")) {
            chain.doFilter(req, res);
            return;
        }

        String providedKey = request.getHeader(HEADER_NAME);

        if (providedKey == null || !expectedApiKey.equals(providedKey)) {
            log.warn("Accès refusé /internal — clé API invalide ou absente depuis {}",
                    request.getRemoteAddr());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Clé API interne invalide ou absente\"}");
            return;
        }

        chain.doFilter(req, res);
    }
}
