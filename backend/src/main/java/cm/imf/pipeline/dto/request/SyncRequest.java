package cm.imf.pipeline.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Requête de synchronisation en lot — envoyée par l'app Flutter
 * lorsqu'elle passe de hors-ligne à en ligne.
 *
 * Le client envoie toutes ses collectes en attente en une seule requête.
 * Le serveur traite chaque item indépendamment et retourne un résultat détaillé.
 */
public record SyncRequest(

        /** UUID généré côté Flutter pour identifier cette session de sync. */
        @NotBlank(message = "L'identifiant de synchronisation est obligatoire")
        String syncId,

        /** Identifiant de l'appareil (ex: UUID Flutter device). */
        @NotBlank(message = "L'identifiant de l'appareil est obligatoire")
        @Size(max = 100)
        String deviceId,

        /** Horodatage de déclenchement de la sync côté client. */
        @NotNull(message = "L'horodatage de synchronisation est obligatoire")
        OffsetDateTime clientSyncTimestamp,

        /** Liste des collectes à synchroniser (max 200 par batch). */
        @NotNull @Size(min = 1, max = 200,
                message = "Le lot doit contenir entre 1 et 200 collectes")
        @Valid
        List<CollecteRequest> items
) {}
