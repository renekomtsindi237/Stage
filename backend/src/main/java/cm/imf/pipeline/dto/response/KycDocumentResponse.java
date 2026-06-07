package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.KycDocument;
import cm.imf.pipeline.enums.TypeDocumentKyc;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record KycDocumentResponse(
        String uid,
        String dossierUid,
        TypeDocumentKyc typeDocument,
        String nomFichier,
        String mimeType,
        Long tailleOctets,
        LocalDate dateExpirationDoc,
        Boolean valide,
        String motifRejet,
        String verifiePar,
        OffsetDateTime dateVerification,
        OffsetDateTime createdAt
) {
    public static KycDocumentResponse from(KycDocument d) {
        return new KycDocumentResponse(
                d.getUid() != null ? d.getUid().toString() : null,
                d.getDossier() != null && d.getDossier().getUid() != null
                        ? d.getDossier().getUid().toString() : null,
                d.getTypeDocument(),
                d.getNomFichier(),
                d.getMimeType(),
                d.getTailleOctets(),
                d.getDateExpirationDoc(),
                d.getValide(),
                d.getMotifRejet(),
                d.getVerifiePar() != null ? d.getVerifiePar().getUsername() : null,
                d.getDateVerification(),
                d.getCreatedAt()
        );
    }
}
