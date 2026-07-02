package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Réponse retournée UNE SEULE FOIS à la création d'un client API.
 * La clé brute (apiKey) n'est jamais ré-affichée — seul le préfixe est stocké.
 */
public record ApiClientCreatedResponse(
        UUID id,
        String name,
        String description,
        String imfNom,
        /** Clé brute complète — à copier immédiatement, non récupérable. */
        String apiKey,
        /** Préfixe affiché dans l'UI pour identifier la clé. */
        String keyPrefix,
        String scopes,
        String statut,
        OffsetDateTime createdAt
) {}
