package cm.imf.pipeline.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Injecte un identifiant de corrélation dans chaque requête HTTP.
 *
 * Si le client envoie X-Request-Id, il est réutilisé (traçabilité bout-en-bout
 * depuis l'app mobile). Sinon un UUID est généré côté serveur.
 *
 * L'identifiant est :
 *   - retourné dans la réponse (X-Request-Id)
 *   - injecté dans le MDC Logback → visible dans tous les logs de la requête
 *   - accessible via X-Api-Version pour documenter la version du contrat
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    static final String REQUEST_ID_HEADER  = "X-Request-Id";
    static final String API_VERSION_HEADER = "X-Api-Version";
    static final String API_VERSION        = "1";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put("requestId", requestId);
        MDC.put("method",    request.getMethod());
        MDC.put("uri",       request.getRequestURI());

        response.setHeader(REQUEST_ID_HEADER,  requestId);
        response.setHeader(API_VERSION_HEADER, API_VERSION);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
