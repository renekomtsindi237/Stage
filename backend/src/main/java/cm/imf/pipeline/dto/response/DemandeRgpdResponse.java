package cm.imf.pipeline.dto.response;

import java.time.OffsetDateTime;

public record DemandeRgpdResponse(
        String uid,
        String demandeurUsername,
        String typeDroit,
        String perimetre,
        String finaliteConcernee,
        String statut,
        OffsetDateTime dateSoumission,
        OffsetDateTime dateLimiteReponse,
        OffsetDateTime dateTraitement,
        String traiteParUsername,
        String reponse,
        /** Nombre de jours restants avant le délai légal (négatif = en retard). */
        Long joursRestants,
        OffsetDateTime createdAt
) {}
