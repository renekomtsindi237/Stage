package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.KycVerification;
import cm.imf.pipeline.enums.NiveauKyc;
import cm.imf.pipeline.enums.ResultatVerificationKyc;
import cm.imf.pipeline.enums.StatutKyc;

import java.time.OffsetDateTime;

public record KycVerificationResponse(
        String uid,
        String dossierUid,
        String verificateurUsername,
        StatutKyc ancienStatut,
        StatutKyc nouveauStatut,
        NiveauKyc ancienNiveau,
        NiveauKyc nouveauNiveau,
        ResultatVerificationKyc resultat,
        String commentaire,
        String motifRejet,
        OffsetDateTime createdAt
) {
    public static KycVerificationResponse from(KycVerification v) {
        return new KycVerificationResponse(
                v.getUid() != null ? v.getUid().toString() : null,
                v.getDossier() != null && v.getDossier().getUid() != null
                        ? v.getDossier().getUid().toString() : null,
                v.getVerificateur() != null ? v.getVerificateur().getUsername() : null,
                v.getAncienStatut(),
                v.getNouveauStatut(),
                v.getAncienNiveau(),
                v.getNouveauNiveau(),
                v.getResultat(),
                v.getCommentaire(),
                v.getMotifRejet(),
                v.getCreatedAt()
        );
    }
}
