package cm.imf.pipeline.dto.response;

import java.util.List;

/**
 * Dossier client complet pour la consultation directeur (identité, créances, KYC, collectes).
 */
public record ClientDossierResponse(
        String idClient,
        String nomClient,
        String telephoneClient,
        String telephoneSecondaire,
        String agencePrincipale,
        String zoneId,
        boolean actif,
        Double encours,
        Integer maxJoursRetard,
        String statut,
        String dateNaissance,
        String sexe,
        String secteurPrincipal,
        String sousSecteur,
        Integer anneesExperience,
        Double revenuMensuelEstime,
        String marchePrincipal,
        String frequenceMarche,
        String niveauEducation,
        String situationFamiliale,
        Integer nombrePersonnesCharge,
        Double latitudeActivite,
        Double longitudeActivite,
        String adresseActivite,
        String createdAt,
        KycResume kyc,
        List<CreanceResume> creances,
        List<CollecteResume> collectes
) {
    public record KycResume(
            String uid,
            String statut,
            String niveauActuel,
            String niveauRisque,
            Integer scoreRisque,
            String dateExpirationKyc,
            String typePieceIdentite,
            String numeroPiece
    ) {}

    public record CreanceResume(
            String idPret,
            String statut,
            Double montantInitial,
            Double montantImpaye,
            Double capitalRestantDu,
            Integer joursRetard,
            String categoriePar,
            String typeGarantie,
            String dateDeblocage,
            String dateOuverture
    ) {}

    public record CollecteResume(
            String idCollecte,
            Double montant,
            String canalPaiement,
            String dateCollecte,
            String statut,
            String agentUsername
    ) {}
}
