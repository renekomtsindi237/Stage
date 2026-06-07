package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.AccordReechelonnement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AccordReechelonnementResponse(
        String uid,
        String dossierUid,
        BigDecimal nouveauMontantMensuel,
        int nombreNouvellesEcheances,
        LocalDate dateDebutNouvelEcheancier,
        BigDecimal tauxInteretAnnuel,
        String approuveParUsername,
        LocalDate dateSignature,
        String observations,
        boolean actif,
        OffsetDateTime createdAt
) {
    public static AccordReechelonnementResponse from(AccordReechelonnement a) {
        return new AccordReechelonnementResponse(
                a.getUid() != null ? a.getUid().toString() : null,
                a.getDossier() != null && a.getDossier().getUid() != null
                        ? a.getDossier().getUid().toString() : null,
                a.getNouveauMontantMensuel(),
                a.getNombreNouvellesEcheances(),
                a.getDateDebutNouvelEcheancier(),
                a.getTauxInteretAnnuel(),
                a.getApprouvePar() != null ? a.getApprouvePar().getUsername() : null,
                a.getDateSignature(),
                a.getObservations(),
                a.isActif(),
                a.getCreatedAt()
        );
    }
}
