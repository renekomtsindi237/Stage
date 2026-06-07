package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;

/**
 * Représentation d'un agent terrain.
 *
 * Les champs GPS (latitude, longitude, precisionMetres, dernierePositionAt)
 * sont null si l'agent n'a jamais partagé sa position ou a désactivé le partage.
 * enLigne = true si Redis marque l'agent connecté (JWT actif < 5 min).
 * enDeplacement = true si dernier ping GPS < 15 min et position_active = TRUE.
 */
public record AgentResponse(
        String  uid,
        String  username,
        String  nomComplet,
        String  idAgence,
        String  nomAgence,
        String  villeAgence,
        String  telephone,

        /** Dernière latitude connue. Null si pas de partage GPS. */
        Double  latitude,
        /** Dernière longitude connue. Null si pas de partage GPS. */
        Double  longitude,
        /** Précision GPS en mètres (±). Null si non disponible. */
        Double  precisionMetres,

        /** Vrai si l'agent est connecté (JWT actif via Redis TTL 5 min). */
        boolean enLigne,
        /**
         * Vrai si le dernier ping GPS date de moins de 15 min
         * et le partage de position est activé.
         */
        boolean enDeplacement,

        OffsetDateTime dernierePositionAt
) {}
