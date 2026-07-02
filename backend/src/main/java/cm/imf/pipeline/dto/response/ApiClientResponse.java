package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Vue liste — la clé brute n'est jamais incluse ici. */
public record ApiClientResponse(
        UUID id,
        String name,
        String description,
        String imfNom,
        String keyPrefix,
        String scopes,
        String statut,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        OffsetDateTime revokedAt
) {}
