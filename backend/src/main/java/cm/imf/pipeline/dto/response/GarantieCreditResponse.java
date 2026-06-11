package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.GarantieCredit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record GarantieCreditResponse(
        UUID uid,
        Long dossierId,
        String type,
        String description,
        BigDecimal valeurEstimee,
        String referenceDocument,
        String cautionNom,
        String cautionTelephone,
        String statut,
        OffsetDateTime createdAt
) {
    public static GarantieCreditResponse from(GarantieCredit g) {
        return new GarantieCreditResponse(
                g.getUid(), g.getDossierId(), g.getType(), g.getDescription(),
                g.getValeurEstimee(), g.getReferenceDocument(), g.getCautionNom(),
                g.getCautionTelephone(), g.getStatut(), g.getCreatedAt()
        );
    }
}
