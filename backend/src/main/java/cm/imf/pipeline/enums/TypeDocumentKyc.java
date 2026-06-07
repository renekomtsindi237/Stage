package cm.imf.pipeline.enums;

public enum TypeDocumentKyc {
    // ── Pièces d'identité ────────────────────────────────────────────────────
    CNI_RECTO,              // Carte Nationale d'Identité — face recto
    CNI_VERSO,              // Carte Nationale d'Identité — face verso
    PASSEPORT,              // Passeport biométrique
    PERMIS_CONDUIRE,        // Permis de conduire (accepté en complément)
    CARTE_SEJOUR,           // Carte de séjour (ressortissants étrangers)

    // ── Justificatifs domicile (Niveau 2) ────────────────────────────────────
    JUSTIFICATIF_DOMICILE,  // Facture CAMWATER, ENEO, AES-SONEL ou CIE ≤ 3 mois
    CERTIFICAT_RESIDENCE,   // Certificat de résidence délivré par le chef de quartier
    CONTRAT_BAIL,           // Contrat de bail signé

    // ── Activité professionnelle & revenus (Niveau 2) ────────────────────────
    FICHE_PAIE,             // Fiche de paie (1 à 3 derniers mois)
    CONTRAT_TRAVAIL,        // Contrat de travail
    DECLARATION_ACTIVITE,   // Déclaration d'activité commerciale / artisanale
    REGISTRE_COMMERCE,      // Registre du Commerce et du Crédit Mobilier (RCCM)
    EXTRAIT_BANCAIRE,       // Relevé de compte bancaire (3 derniers mois)

    // ── Diligence renforcée (Niveau 3) ──────────────────────────────────────
    DECLARATION_SOURCE_FONDS, // Déclaration d'origine des fonds (PPE / montants élevés)
    ATTESTATION_PPE,          // Attestation de Personne Politiquement Exposée
    PHOTO_BIOMETRIQUE,        // Photo d'identité récente (format biométrique)
    AUTRE                     // Tout document non catégorisé
}
