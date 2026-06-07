package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.enums.NiveauKyc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ImfResponse(
        String uid,
        String code,
        String nom,
        String pays,
        boolean actif,
        OffsetDateTime createdAt,

        // Constitution
        String denominationSociale,
        String adresseSiege,
        String formeJuridique,
        BigDecimal capitalSocial,
        String numAgrement,
        String telephone,
        String email,

        // Paramètres crédit
        BigDecimal tauxInteretAnnuel,
        Integer dureeMaxCreditMois,
        BigDecimal tauxPenaliteRetard,
        Integer seuilRelanceJours,

        // Paramètres épargne
        BigDecimal tauxEpargne,
        BigDecimal soldeMinEpargne,
        BigDecimal fraisTenueCompte,

        // Segmentation
        String segmentsClients,
        String typesGaranties,

        // Paramètres opérationnels adaptables
        Long maxDocumentKycOctets,
        NiveauKyc niveauKycMinimal,
        Integer maxTentativesConnexion,

        // Logo
        String logoUrl,

        // Indicateur DSI
        boolean hasDsi
) {
    public static ImfResponse of(Imf imf, boolean hasDsi) {
        return new ImfResponse(
                imf.getUid() != null ? imf.getUid().toString() : null,
                imf.getCode(),
                imf.getNom(),
                imf.getPays(),
                imf.isActif(),
                imf.getCreatedAt(),
                imf.getDenominationSociale(),
                imf.getAdresseSiege(),
                imf.getFormeJuridique(),
                imf.getCapitalSocial(),
                imf.getNumAgrement(),
                imf.getTelephone(),
                imf.getEmail(),
                imf.getTauxInteretAnnuel(),
                imf.getDureeMaxCreditMois(),
                imf.getTauxPenaliteRetard(),
                imf.getSeuilRelanceJours(),
                imf.getTauxEpargne(),
                imf.getSoldeMinEpargne(),
                imf.getFraisTenueCompte(),
                imf.getSegmentsClients(),
                imf.getTypesGaranties(),
                imf.getMaxDocumentKycOctets(),
                imf.getNiveauKycMinimal(),
                imf.getMaxTentativesConnexion(),
                imf.getLogoUrl(),
                hasDsi
        );
    }

    public static ImfResponse from(Imf imf) {
        return of(imf, false);
    }
}
