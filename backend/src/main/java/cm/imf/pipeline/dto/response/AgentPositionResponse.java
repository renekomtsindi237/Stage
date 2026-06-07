package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;

/**
 * Position géolocalisée d'un agent terrain.
 *
 * Utilisé dans :
 *  - GET /api/agents/positions          (liste carte temps réel)
 *  - GET /api/agents/{uid}/positions/historique (trajet journalier)
 *  - Payload SSE AGENT_POSITION_UPDATED
 */
public record AgentPositionResponse(

        String agentUid,
        String username,
        String nomComplet,
        String nomAgence,
        String villeAgence,

        double latitude,
        double longitude,

        /** Précision GPS en mètres (±). Null si non disponible. */
        Double precisionMetres,

        /** Altitude en mètres. Null si non disponible. */
        Double altitudeMetres,

        /** Vitesse de déplacement en km/h. Null si non disponible. */
        Double vitesseKmh,

        /**
         * Vrai si la position est récente (< 15 min) et le partage activé.
         * False = agent hors ligne ou partage désactivé.
         */
        boolean enDeplacement,

        /**
         * Source du dernier ping GPS.
         * MOBILE : ping autonome.  COLLECTE : position d'une collecte.
         */
        String source,

        OffsetDateTime capturedAt
) {}
