package cm.imf.pipeline.ml;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

/**
 * Client HTTP vers le service FastAPI MCRS (port 8090).
 * Toutes les méthodes sont non-bloquantes dans le sens où elles retournent
 * Optional.empty() si le service est indisponible plutôt que de lever une exception.
 */
@Slf4j
@Component
public class MlScoringClient {

    private final RestClient mlRestClient;

    public MlScoringClient(@Qualifier("mlRestClient") RestClient mlRestClient) {
        this.mlRestClient = mlRestClient;
    }

    /**
     * Retourne les métadonnées du modèle MCRS actif (version, métriques AUC, features).
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> modelInfo() {
        try {
            Map<String, Object> info = mlRestClient.get()
                    .uri("/model/info")
                    .retrieve()
                    .body(Map.class);
            return Optional.ofNullable(info);
        } catch (RestClientException e) {
            log.warn("ML API /model/info indisponible : {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Vérifie que le service ML est opérationnel et que le modèle est chargé.
     * Retourne Optional.empty() si le service ne répond pas (503 ou timeout).
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> modelHealth() {
        try {
            Map<String, Object> health = mlRestClient.get()
                    .uri("/model/health")
                    .retrieve()
                    .body(Map.class);
            return Optional.ofNullable(health);
        } catch (RestClientException e) {
            log.warn("ML API /model/health indisponible : {}", e.getMessage());
            return Optional.empty();
        }
    }
}
