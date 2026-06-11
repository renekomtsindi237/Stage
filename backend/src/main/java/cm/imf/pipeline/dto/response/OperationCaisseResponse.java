package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.OperationCaisse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record OperationCaisseResponse(
        Long id,
        Long caissierId,
        Long imfId,
        String type,
        BigDecimal montant,
        String reference,
        String pretId,
        String clientId,
        OffsetDateTime dateOperation,
        BigDecimal soldeAvant,
        BigDecimal soldeApres,
        OffsetDateTime createdAt
) {
    public static OperationCaisseResponse from(OperationCaisse o) {
        return new OperationCaisseResponse(
                o.getId(), o.getCaissierId(), o.getImfId(), o.getType(),
                o.getMontant(), o.getReference(), o.getPretId(), o.getClientId(),
                o.getDateOperation(), o.getSoldeAvant(), o.getSoldeApres(), o.getCreatedAt()
        );
    }
}
