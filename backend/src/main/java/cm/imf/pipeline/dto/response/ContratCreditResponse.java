package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.ContratCredit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ContratCreditResponse(
        UUID uid,
        Long dossierId,
        String referenceContrat,
        LocalDate dateSignature,
        BigDecimal montantFinal,
        BigDecimal tauxInteret,
        BigDecimal fraisDossier,
        Integer nbEcheances,
        String periodicite,
        boolean signaturesConformes,
        Long agentSaisieId,
        OffsetDateTime dateGeneration,
        String urlContratPdf,
        String statut,
        OffsetDateTime createdAt
) {
    public static ContratCreditResponse from(ContratCredit c) {
        return new ContratCreditResponse(
                c.getUid(), c.getDossierId(), c.getReferenceContrat(), c.getDateSignature(),
                c.getMontantFinal(), c.getTauxInteret(), c.getFraisDossier(), c.getNbEcheances(),
                c.getPeriodicite(), c.isSignaturesConformes(), c.getAgentSaisieId(),
                c.getDateGeneration(), c.getUrlContratPdf(), c.getStatut(), c.getCreatedAt()
        );
    }
}
