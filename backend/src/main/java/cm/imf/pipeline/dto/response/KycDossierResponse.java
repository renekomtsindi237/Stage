package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.KycDossier;
import cm.imf.pipeline.enums.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record KycDossierResponse(
        String uid,
        String imfUid,
        String clientId,
        String nomClient,
        String prenomClient,
        LocalDate dateNaissance,
        String lieuNaissance,
        String nationalite,
        String telephone,
        String email,
        String adresse,
        String ville,
        String profession,
        String employeur,
        BigDecimal revenuMensuelEstim,

        TypeDocumentKyc typePieceIdentite,
        String numeroPiece,
        LocalDate dateEmissionPiece,
        LocalDate dateExpirationPiece,
        String lieuEmissionPiece,

        NiveauKyc niveauActuel,
        NiveauKyc niveauDemande,
        StatutKyc statut,

        int scoreRisque,
        NiveauRisque niveauRisque,
        boolean estPep,
        String motifRisqueEleve,

        boolean verifSanctions,
        boolean verifListesNoires,

        String verificateurUsername,
        OffsetDateTime dateVerification,
        LocalDate dateExpirationKyc,
        String observations,

        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static KycDossierResponse from(KycDossier d) {
        return new KycDossierResponse(
                d.getUid() != null ? d.getUid().toString() : null,
                d.getImf() != null && d.getImf().getUid() != null ? d.getImf().getUid().toString() : null,
                d.getClientId(),
                d.getNomClient(),
                d.getPrenomClient(),
                d.getDateNaissance(),
                d.getLieuNaissance(),
                d.getNationalite(),
                d.getTelephone(),
                d.getEmail(),
                d.getAdresse(),
                d.getVille(),
                d.getProfession(),
                d.getEmployeur(),
                d.getRevenuMensuelEstim(),
                d.getTypePieceIdentite(),
                d.getNumeroPiece(),
                d.getDateEmissionPiece(),
                d.getDateExpirationPiece(),
                d.getLieuEmissionPiece(),
                d.getNiveauActuel(),
                d.getNiveauDemande(),
                d.getStatut(),
                d.getScoreRisque(),
                d.getNiveauRisque(),
                d.isEstPep(),
                d.getMotifRisqueEleve(),
                d.isVerifSanctions(),
                d.isVerifListesNoires(),
                d.getVerificateur() != null ? d.getVerificateur().getUsername() : null,
                d.getDateVerification(),
                d.getDateExpirationKyc(),
                d.getObservations(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
