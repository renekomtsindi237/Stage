package cm.imf.pipeline.enums;

/**
 * Niveaux KYC conformes aux directives BEAC/COBAC :
 * NIVEAU_1  — identité de base (CNI + données biographiques) — opérations &lt; 150 000 FCFA/mois
 * NIVEAU_2  — identité renforcée + justificatif domicile + activité professionnelle
 * NIVEAU_3  — diligence renforcée : source de fonds, PPE, relations d'affaires, vérification COBAC
 */
public enum NiveauKyc {
    NIVEAU_1,
    NIVEAU_2,
    NIVEAU_3
}
