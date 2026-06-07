package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ConnectivityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint ultra-léger de vérification de connectivité.
 *
 * Utilisé par l'app Flutter pour détecter si le serveur est joignable
 * avant de déclencher une synchronisation. Pas d'authentification requise.
 *
 * Réponse typique en < 5ms (aucun accès DB ou cache).
 * Cache-Control: no-store pour éviter les faux positifs de cache.
 */
@RestController
@Tag(name = "Connectivité", description = "Vérification de disponibilité du serveur (ping)")
public class ConnectivityController {

    @Value("${spring.application.name:imf-pipeline-backend}")
    private String appName;

    @Value("${imf.pipeline.version:1.0.0}")
    private String version;

    @Operation(
            summary = "Ping serveur — vérification de connectivité",
            description = """
                    Retourne immédiatement si le serveur est joignable.
                    Utilisé par l'app mobile pour basculer entre les modes
                    hors-ligne et en ligne.

                    Aucune authentification requise.
                    Réponse Cache-Control: no-store.
                    """
    )
    @GetMapping("/ping")
    public ResponseEntity<ConnectivityResponse> ping() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ConnectivityResponse.enLigne(version));
    }

    @Operation(summary = "Health check minimal (alias /api/ping pour compatibilité)")
    @GetMapping("/health")
    public ResponseEntity<ConnectivityResponse> health() {
        return ping();
    }
}
