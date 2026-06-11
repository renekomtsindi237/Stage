package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.DossierCredit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DossierCreditResponse(
        UUID uid,
        Long imfId,
        Long agenceId,
        Long agentCreditId,
        String clientId,
        String clientNom,
        BigDecimal montantDemande,
        Integer dureeMois,
        String objetFinancement,
        String secteurActivite,
        BigDecimal revenuEstime,
        BigDecimal chargesMensuelles,
        BigDecimal capaciteRemboursement,
        String statut,
        String noteAnalyse,
        OffsetDateTime dateSoumission,
        OffsetDateTime dateDecision,
        Long chefAgenceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DossierCreditResponse from(DossierCredit d) {
        return new DossierCreditResponse(
                d.getUid(), d.getImfId(), d.getAgenceId(), d.getAgentCreditId(),
                d.getClientId(), d.getClientNom(), d.getMontantDemande(), d.getDureeMois(),
                d.getObjetFinancement(), d.getSecteurActivite(), d.getRevenuEstime(),
                d.getChargesMensuelles(), d.getCapaciteRemboursement(), d.getStatut(),
                d.getNoteAnalyse(), d.getDateSoumission(), d.getDateDecision(),
                d.getChefAgenceId(), d.getCreatedAt(), d.getUpdatedAt()
        );
    }
}
