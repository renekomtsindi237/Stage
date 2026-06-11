package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.PlanApurement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PlanApurementResponse(
        UUID uid,
        Long dossierId,
        Integer nbEcheances,
        BigDecimal montantParEcheance,
        LocalDate dateDebut,
        boolean signeClient,
        String statut,
        OffsetDateTime createdAt
) {
    public static PlanApurementResponse from(PlanApurement p) {
        return new PlanApurementResponse(
                p.getUid(), p.getDossierId(), p.getNbEcheances(), p.getMontantParEcheance(),
                p.getDateDebut(), p.isSigneClient(), p.getStatut(), p.getCreatedAt()
        );
    }
}
