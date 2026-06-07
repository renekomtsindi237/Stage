package cm.imf.pipeline.enums;

public enum StatutKyc {
    EN_ATTENTE,             // Dossier ouvert, documents non encore soumis
    DOCUMENTS_SOUMIS,       // Documents uploadés, en attente de vérification
    EN_COURS_VERIFICATION,  // Examinateur assigné, vérification en cours
    COMPLEMENT_REQUIS,      // Vérificateur demande des pièces supplémentaires
    APPROUVE,               // KYC validé — client actif
    REJETE,                 // Refus définitif (motif obligatoire)
    EXPIRE,                 // Documents expirés — renouvellement requis
    SUSPENDU                // Suspension temporaire (enquête LBC/FT)
}
