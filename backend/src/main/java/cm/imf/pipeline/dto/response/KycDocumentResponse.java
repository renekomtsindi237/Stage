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
        OffsetDateTime createdAt,
        /** URL relative pour télécharger/prévisualiser le document — null si indisponible */
        String documentUrl
) {
    public static KycDocumentResponse from(KycDocument d) {
        String uid = d.getUid() != null ? d.getUid().toString() : null;
        boolean hasContenu = (d.getCheminStockage() != null && !d.getCheminStockage().isBlank())
                          || (d.getContenuBase64()  != null && !d.getContenuBase64().isBlank());
        return new KycDocumentResponse(
                uid,
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
                d.getCreatedAt(),
                (uid != null && hasContenu) ? "/api/v1/kyc/documents/" + uid + "/download" : null
        );
    }
}
