package cm.imf.pipeline.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests RequestIdFilter — traçabilité X-Request-Id par requête.
 * Garantit que chaque réponse contient un identifiant de corrélation
 * et que la version API est retournée.
 */
@DisplayName("RequestIdFilter — traçabilité requêtes")
class RequestIdFilterTest {

    private RequestIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestIdFilter();
    }

    // ── Génération X-Request-Id ───────────────────────────────────────────────

    @Test
    @DisplayName("→ génère un UUID si X-Request-Id absent de la requête entrante")
    void genere_uuid_si_absent() throws Exception {
        MockHttpServletRequest  req  = new MockHttpServletRequest("GET", "/clients");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        String requestId = resp.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank();
        // Doit être un UUID valide
        assertThatCode(() -> java.util.UUID.fromString(requestId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("→ retransmet l'ID fourni par le client (corrélation end-to-end)")
    void retransmet_id_client_existant() throws Exception {
        String clientId = "my-correlation-id-42";
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/collectes");
        req.addHeader(RequestIdFilter.REQUEST_ID_HEADER, clientId);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, mock(FilterChain.class));

        assertThat(resp.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(clientId);
    }

    // ── X-Api-Version ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("→ retourne X-Api-Version: 1 dans chaque réponse")
    void retourne_version_api() throws Exception {
        MockHttpServletRequest  req  = new MockHttpServletRequest("GET", "/kpi");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, mock(FilterChain.class));

        assertThat(resp.getHeader(RequestIdFilter.API_VERSION_HEADER))
                .isEqualTo(RequestIdFilter.API_VERSION);
    }

    // ── MDC ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("→ MDC nettoyé après traitement (pas de fuite entre requêtes)")
    void mdc_nettoye_apres_traitement() throws Exception {
        MockHttpServletRequest  req  = new MockHttpServletRequest("GET", "/test");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, mock(FilterChain.class));

        // Après le filter, le MDC doit être vide
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("method")).isNull();
        assertThat(MDC.get("uri")).isNull();
    }

    @Test
    @DisplayName("→ MDC nettoyé même si la chain lève une exception")
    void mdc_nettoye_meme_si_exception() throws Exception {
        MockHttpServletRequest  req  = new MockHttpServletRequest("POST", "/kyc/dossiers");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doThrow(new RuntimeException("downstream error")).when(chain).doFilter(req, resp);

        assertThatThrownBy(() -> filter.doFilterInternal(req, resp, chain))
                .isInstanceOf(RuntimeException.class);

        // MDC toujours nettoyé dans le bloc finally
        assertThat(MDC.get("requestId")).isNull();
    }

    // ── Chaîne de filtrage ────────────────────────────────────────────────────

    @Test
    @DisplayName("→ la chain est toujours appelée (le filtre ne bloque pas)")
    void chain_toujours_appelee() throws Exception {
        MockHttpServletRequest  req  = new MockHttpServletRequest("GET", "/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        verify(chain, times(1)).doFilter(req, resp);
    }

    @Test
    @DisplayName("→ IDs distincts pour deux requêtes différentes")
    void deux_requetes_ids_differents() throws Exception {
        MockHttpServletRequest  req1  = new MockHttpServletRequest("GET", "/a");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        MockHttpServletRequest  req2  = new MockHttpServletRequest("GET", "/b");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();

        filter.doFilterInternal(req1, resp1, mock(FilterChain.class));
        filter.doFilterInternal(req2, resp2, mock(FilterChain.class));

        assertThat(resp1.getHeader(RequestIdFilter.REQUEST_ID_HEADER))
                .isNotEqualTo(resp2.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    }
}
