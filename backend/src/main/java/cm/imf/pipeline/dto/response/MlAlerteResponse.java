package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;

public record MlAlerteResponse(
        Long id,
        String clientIdExterne,
        String typeAlerte,
        String urgence,
        String titre,
        String description,
        String recommandation,
        String statut,
        OffsetDateTime createdAt
) {}
