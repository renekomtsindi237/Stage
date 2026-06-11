package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.Decaissement;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DecaissementResponse(
        UUID uid,
        Long contratId,
        Long caissierId,
        BigDecimal montantNet,
        String mode,
        String referencePaiement,
        OffsetDateTime dateDecaissement,
        Long autoriseParId,
        String statut,
        OffsetDateTime createdAt
) {
    public static DecaissementResponse from(Decaissement d) {
        return new DecaissementResponse(
                d.getUid(), d.getContratId(), d.getCaissierId(), d.getMontantNet(),
                d.getMode(), d.getReferencePaiement(), d.getDateDecaissement(),
                d.getAutoriseParId(), d.getStatut(), d.getCreatedAt()
        );
    }
}
