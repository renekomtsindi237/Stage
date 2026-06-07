package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.CollecteTerrain;
import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.enums.StatutCollecte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CollecteResponse(
        String uid,
        String idCollecteMobile,
        String clientId,
        String pretId,
        LocalDate dateCollecte,
        BigDecimal montantCollecte,
        CanalPaiement canalPaiement,
        String referenceTransaction,
        StatutCollecte statut,
        OffsetDateTime createdAt
) {
    public static CollecteResponse from(CollecteTerrain c) {
        return new CollecteResponse(
                c.getUid() != null ? c.getUid().toString() : null,
                c.getIdCollecteMobile(),
                c.getClientId(),
                c.getPretId(),
                c.getDateCollecte(),
                c.getMontantCollecte(),
                c.getCanalPaiement(),
                c.getReferenceTransaction(),
                c.getStatut(),
                c.getCreatedAt()
        );
    }
}
