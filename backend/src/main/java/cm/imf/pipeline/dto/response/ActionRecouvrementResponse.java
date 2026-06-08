package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.ActionRecouvrement;
import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.enums.ResultatActionRecouvrement;
import cm.imf.pipeline.enums.StatutVerifMomo;
import cm.imf.pipeline.enums.TypeActionRecouvrement;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ActionRecouvrementResponse(
        String uid,
        String dossierUid,
        TypeActionRecouvrement typeAction,
        OffsetDateTime dateAction,
        String agentUsername,
        ResultatActionRecouvrement resultat,
        LocalDate promesseDate,
        BigDecimal promesseMontant,

        // Mobile Money
        CanalPaiement canalPaiement,
        String referenceTransaction,
        String numeroTelephonePaiement,
        StatutVerifMomo statutVerifMomo,

        // Frais
        BigDecimal fraisEngages,

        String observation,
        OffsetDateTime createdAt
) {
        @JsonProperty("montantRecupere")
        public BigDecimal montantRecupere() {
                return promesseMontant;
        }

    public static ActionRecouvrementResponse from(ActionRecouvrement a) {
        return new ActionRecouvrementResponse(
                a.getUid() != null ? a.getUid().toString() : null,
                a.getDossier() != null && a.getDossier().getUid() != null
                        ? a.getDossier().getUid().toString() : null,
                a.getTypeAction(),
                a.getDateAction(),
                a.getAgent() != null ? a.getAgent().getUsername() : null,
                a.getResultat(),
                a.getPromesseDate(),
                a.getPromesseMontant(),
                a.getCanalPaiement(),
                a.getReferenceTransaction(),
                a.getNumeroTelephonePaiement(),
                a.getStatutVerifMomo(),
                a.getFraisEngages(),
                a.getObservation(),
                a.getCreatedAt()
        );
    }
}
