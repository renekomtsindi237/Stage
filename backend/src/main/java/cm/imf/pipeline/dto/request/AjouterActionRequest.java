package cm.imf.pipeline.dto.request;

import cm.imf.pipeline.enums.CanalPaiement;
import cm.imf.pipeline.enums.ResultatActionRecouvrement;
import cm.imf.pipeline.enums.StatutVerifMomo;
import cm.imf.pipeline.enums.TypeActionRecouvrement;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AjouterActionRequest(
        @NotNull TypeActionRecouvrement typeAction,
        ResultatActionRecouvrement resultat,
        LocalDate promesseDate,
        BigDecimal promesseMontant,

        // Paiement Mobile Money
        CanalPaiement canalPaiement,
        String referenceTransaction,
        /** Numéro de téléphone MoMo/OM (format camerounais : 6XXXXXXXX) */
        String numeroTelephonePaiement,
        StatutVerifMomo statutVerifMomo,

        /** Frais engendrés par cette action en FCFA (huissier, déplacement, avocat…) */
        BigDecimal fraisEngages,

        String observation
) {}
