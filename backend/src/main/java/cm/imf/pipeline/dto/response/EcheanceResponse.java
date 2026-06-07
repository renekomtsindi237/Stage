package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.EcheanceApp;
import cm.imf.pipeline.enums.StatutEcheance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record EcheanceResponse(
        String uid,
        String idPret,
        String agentUid,
        String agentUsername,
        int numEcheance,
        LocalDate dateEcheance,
        BigDecimal montantDu,
        BigDecimal montantPaye,
        BigDecimal resteAPayer,
        LocalDate datePaiement,
        StatutEcheance statut,
        String collecteUid,
        String observation,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static EcheanceResponse from(EcheanceApp e) {
        BigDecimal reste = e.getMontantDu().subtract(e.getMontantPaye());
        return new EcheanceResponse(
                e.getUid() != null ? e.getUid().toString() : null,
                e.getIdPret(),
                e.getAgent() != null && e.getAgent().getUid() != null ? e.getAgent().getUid().toString() : null,
                e.getAgent() != null ? e.getAgent().getUsername() : null,
                e.getNumEcheance(),
                e.getDateEcheance(),
                e.getMontantDu(),
                e.getMontantPaye(),
                reste.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : reste,
                e.getDatePaiement(),
                e.getStatut(),
                e.getCollecte() != null && e.getCollecte().getUid() != null ? e.getCollecte().getUid().toString() : null,
                e.getObservation(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
