package cm.imf.pipeline.dto.response;

import java.util.UUID;

public record ApiKeyRevealedResponse(
        UUID id,
        String name,
        String keyPrefix,
        /** Clé brute déchiffrée — afficher uniquement dans un contexte sécurisé. */
        String apiKey
) {}
