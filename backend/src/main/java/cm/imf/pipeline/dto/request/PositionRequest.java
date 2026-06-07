package cm.imf.pipeline.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Corps de la requête PUT /api/agents/me/position.
 *
 * Envoyé par l'application Flutter lors de chaque ping GPS de l'agent.
 * Les champs optionnels (précision, altitude, vitesse, cap) sont transmis
 * si le périphérique GPS les fournit.
 */
public record PositionRequest(

        @NotNull(message = "La latitude est obligatoire")
        @DecimalMin(value = "-90.0",  message = "Latitude invalide")
        @DecimalMax(value =  "90.0",  message = "Latitude invalide")
        Double latitude,

        @NotNull(message = "La longitude est obligatoire")
        @DecimalMin(value = "-180.0", message = "Longitude invalide")
        @DecimalMax(value =  "180.0", message = "Longitude invalide")
        Double longitude,

        /** Précision horizontale du GPS en mètres (±). Null si non fournie. */
        Double precisionMetres,

        /** Altitude en mètres (WGS-84). Null si non fournie. */
        Double altitudeMetres,

        /** Vitesse de déplacement en km/h. Null si l'appareil ne la fournit pas. */
        Double vitesseKmh,

        /**
         * Cap magnétique en degrés (0-360°). Null si non fourni.
         * 0 = Nord, 90 = Est, 180 = Sud, 270 = Ouest.
         */
        Double capDegres,

        /**
         * Source du ping GPS.
         * MOBILE : ping autonome périodique (background).
         * COLLECTE : position captée lors d'une collecte terrain.
         */
        String source,

        /**
         * UUID de la collecte associée (uniquement si source=COLLECTE).
         * Permet la corrélation position ↔ collecte.
         */
        String collecteUuid
) {
    public PositionRequest {
        if (source == null || source.isBlank()) source = "MOBILE";
    }
}
