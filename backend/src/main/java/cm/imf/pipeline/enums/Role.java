package cm.imf.pipeline.enums;

public enum Role {
    SUPER_ADMIN,               // Administrateur plateforme — accès cross-IMF
    DIRECTEUR,
    RESPONSABLE_RECOUVREMENT,
    ANALYSTE,
    DSI,                       // Administrateur d'une IMF
    SUPPORT,                   // Monitoring technique plateforme (cross-IMF, infrastructure)
    AGENT,                     // Agent terrain recouvrement amiable / collecte
    AGENT_CREDIT,              // Chargé de clientèle — octroi, dossiers, garanties
    CHEF_AGENCE,               // Valide dans sa délégation, préside le comité d'agence
    ANALYSTE_ENGAGEMENTS,      // Conformité COBAC, ratios prudentiels, Grand Comité
    AGENT_SAISIE,              // Back-office : contrats, amortissement, signatures
    CAISSIER,                  // Décaissement ordonné, encaissement remboursements
    API_CLIENT                 // Compte de service pour intégrations CBS/BluCash (auth par mot de passe, pas d'OTP)
}
