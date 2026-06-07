package cm.imf.pipeline.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestLoggingFilter — tests unitaires")
class RequestLoggingFilterTest {

    @InjectMocks RequestLoggingFilter filter;
    @Mock HttpServletRequest  request;
    @Mock HttpServletResponse response;
    @Mock FilterChain         chain;

    @Test
    @DisplayName("doFilter — ajoute X-Request-Id dans les headers de réponse")
    void doFilter_ajoute_xRequestId() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/kpi/dashboard-summary");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, chain);

        verify(response).setHeader(eq("X-Request-Id"), anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("doFilter — MDC est nettoyé après la requête (même en cas d'exception)")
    void doFilter_nettoie_mdc_apres_requete() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/collectes");
        when(response.getStatus()).thenReturn(201);

        // Injecte une valeur parasite pour vérifier qu'elle est nettoyée
        MDC.put("requestId", "parasite-value");

        filter.doFilter(request, response, chain);

        // MDC doit être vide après l'exécution
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("doFilter — MDC nettoyé même si FilterChain lève une exception")
    void doFilter_nettoie_mdc_si_exception() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/prets");
        when(response.getStatus()).thenReturn(500);
        doThrow(new RuntimeException("Erreur simulée")).when(chain).doFilter(any(), any());

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(RuntimeException.class);

        // MDC doit toujours être vide
        assertThat(MDC.get("requestId")).isNull();
    }
}
