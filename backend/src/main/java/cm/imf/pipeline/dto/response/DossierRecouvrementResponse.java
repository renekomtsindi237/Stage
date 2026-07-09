package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.RecouvrementDossier;
import cm.imf.pipeline.enums.CategorieCobtac;
import cm.imf.pipeline.enums.RecouvrementPhase;
import cm.imf.pipeline.enums.TypeGarantie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DossierRecouvrementResponse(
        String uid,
        String idPret,
        String nomClient,
        BigDecimal montantImpaye,
        int joursRetard,
        BigDecimal prioriteScoring,

        // Classification COBAC
        CategorieCobtac categorieCobtac,
        BigDecimal tauxProvision,
        BigDecimal montantProvision,
        LocalDate datePremiereEcheanceImpayee,

        // Caution / Garantie
        String nomCaution,
        String telephoneCaution,
        TypeGarantie typeGarantie,

        // Frais de recouvrement cumulés
        BigDecimal fraisRecouvrement,

        // Workflow
        RecouvrementPhase phase,
        OffsetDateTime dateOuverture,
        OffsetDateTime dateDerniereAction,
        String agentResponsableUsername,
        boolean clos,
        OffsetDateTime dateCloture,
        String motifCloture,
        OffsetDateTime updatedAt
) {
    public static DossierRecouvrementResponse from(RecouvrementDossier d) {
        return new DossierRecouvrementResponse(
                d.getUid() != null ? d.getUid().toString() : null,
                d.getIdPret(),
                d.getNomClient(),
                d.getMontantImpaye(),
                d.getJoursRetard(),
                d.getPrioriteScoring(),
                d.getCategorieCobtac(),
                d.getTauxProvision(),
                d.getMontantProvision(),
                d.getDatePremiereEcheanceImpayee(),
                d.getNomCaution(),
                d.getTelephoneCaution(),
                d.getTypeGarantie(),
                d.getFraisRecouvrement(),
                d.getPhase(),
                d.getDateOuverture(),
                d.getDateDerniereAction(),
                d.getAgentResponsable() != null ? d.getAgentResponsable().getUsername() : null,
                d.isClos(),
                d.getDateCloture(),
                d.getMotifCloture(),
                d.getUpdatedAt()
        );
    }
}
