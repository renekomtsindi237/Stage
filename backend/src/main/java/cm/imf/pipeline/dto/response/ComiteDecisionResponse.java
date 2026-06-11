package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.ComiteDecision;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ComiteDecisionResponse(
        UUID uid,
        Long dossierId,
        String typeComite,
        Long presidentId,
        OffsetDateTime dateSeance,
        String decision,
        BigDecimal montantApprouve,
        BigDecimal tauxApprouve,
        Integer dureeApprouvee,
        String conditions,
        boolean quorumAtteint,
        String motifRejet,
        OffsetDateTime createdAt
) {
    public static ComiteDecisionResponse from(ComiteDecision c) {
        return new ComiteDecisionResponse(
                c.getUid(), c.getDossierId(), c.getTypeComite(), c.getPresidentId(),
                c.getDateSeance(), c.getDecision(), c.getMontantApprouve(), c.getTauxApprouve(),
                c.getDureeApprouvee(), c.getConditions(), c.isQuorumAtteint(),
                c.getMotifRejet(), c.getCreatedAt()
        );
    }
}
