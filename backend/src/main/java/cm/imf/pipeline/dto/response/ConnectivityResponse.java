package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;

/**
 * Réponse au ping de connectivité (GET /api/ping).
 * Ultra-léger — permet à l'app mobile de détecter si le serveur est joignable.
 */
public record ConnectivityResponse(
        String statut,
        String message,
        OffsetDateTime serverTime,
        String version
) {
    public static ConnectivityResponse enLigne(String version) {
        return new ConnectivityResponse("EN_LIGNE",
                "Connexion au serveur établie. Synchronisation disponible.",
                OffsetDateTime.now(), version);
    }
}
