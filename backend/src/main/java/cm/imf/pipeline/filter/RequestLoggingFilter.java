package cm.imf.pipeline.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtre HTTP qui injecte un identifiant de requête unique dans le MDC (Mapped Diagnostic Context).
 * Chaque ligne de log inclut automatiquement requestId, method et path pour la traçabilité.
 */
@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    private static final String REQUEST_ID = "requestId";
    private static final String HTTP_METHOD = "method";
    private static final String HTTP_PATH   = "path";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long   startMs   = System.currentTimeMillis();

        MDC.put(REQUEST_ID,  requestId);
        MDC.put(HTTP_METHOD, request.getMethod());
        MDC.put(HTTP_PATH,   request.getRequestURI());

        response.setHeader("X-Request-Id", requestId);

        try {
            log.debug("→ {} {}", request.getMethod(), request.getRequestURI());
            chain.doFilter(req, res);
        } finally {
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("← {} {} {} {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsed);
            MDC.clear();
        }
    }
}
