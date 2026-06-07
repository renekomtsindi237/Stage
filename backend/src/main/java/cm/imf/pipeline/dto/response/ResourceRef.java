package cm.imf.pipeline.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Référence canonique vers une ressource de l'API.
 *
 * Utilisé pour les champs croisés (cross-resource references) dans les réponses.
 * Permet aux clients de naviguer directement vers une ressource liée
 * sans construire eux-mêmes l'URL.
 *
 * Exemple dans PretResponse :
 *   "client": { "uid": "abc...", "type": "clients", "href": "/api/v1/clients/abc..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Référence vers une ressource liée (UID + URL canonique)")
public record ResourceRef(

    @Schema(description = "Identifiant unique public de la ressource", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    String uid,

    @Schema(description = "Type de ressource (nom de collection REST)", example = "clients")
    String type,

    @Schema(description = "URL canonique de la ressource", example = "/api/v1/clients/3fa85f64-5717-4562-b3fc-2c963f66afa6")
    String href

) {
    /** Construit une référence vers une ressource versionnée v1. */
    public static ResourceRef of(String type, String uid) {
        if (uid == null || uid.isBlank()) return null;
        return new ResourceRef(uid, type, "/api/v1/" + type + "/" + uid);
    }

    /** Référence vers une ressource identifiée par un ID externe (CBS, Mobile). */
    public static ResourceRef external(String type, String externalId) {
        if (externalId == null || externalId.isBlank()) return null;
        return new ResourceRef(externalId, type, "/api/v1/" + type + "/" + externalId);
    }
}
