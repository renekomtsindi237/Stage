-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
-- V14 : SystÃ¨me KYC multi-niveaux (COBAC/BEAC â€” Cameroun)
-- RÃ©fÃ©rence rÃ©glementaire :
--   â€¢ RÃ¨glement COBAC R-2005/01 sur la LBC/FT
--   â€¢ Loi NÂ°2003/008 sur la prÃ©vention du blanchiment de capitaux
--   â€¢ Directives BEAC sur la monnaie Ã©lectronique
-- â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

-- â”€â”€ Dossiers KYC (un par client par IMF) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE IF NOT EXISTS app.kyc_dossiers (
    id                  BIGSERIAL PRIMARY KEY,
    imf_id              BIGINT        NOT NULL REFERENCES app.imf(id),
    client_id           VARCHAR(100)  NOT NULL,
    nom_client          VARCHAR(200)  NOT NULL,
    prenom_client       VARCHAR(200),
    date_naissance      DATE,
    lieu_naissance      VARCHAR(200),
    nationalite         VARCHAR(100)  DEFAULT 'Camerounaise',
    telephone           VARCHAR(30),
    email               VARCHAR(150),
    adresse             VARCHAR(500),
    ville               VARCHAR(100),
    profession          VARCHAR(200),
    employeur           VARCHAR(200),
    revenu_mensuel_estim NUMERIC(15,2),

    -- PiÃ¨ce d'identitÃ© principale
    type_piece_identite VARCHAR(40),
    numero_piece        VARCHAR(80),
    date_emission_piece DATE,
    date_expiration_piece DATE,
    lieu_emission_piece VARCHAR(150),

    -- Niveau KYC atteint / demandÃ©
    niveau_actuel       VARCHAR(20)   NOT NULL DEFAULT 'NIVEAU_1',
    niveau_demande      VARCHAR(20)   NOT NULL DEFAULT 'NIVEAU_1',

    -- Statut du dossier
    statut              VARCHAR(30)   NOT NULL DEFAULT 'EN_ATTENTE',

    -- Ã‰valuation du risque (COBAC/BEAC)
    score_risque        INT           DEFAULT 0 CHECK (score_risque BETWEEN 0 AND 100),
    niveau_risque       VARCHAR(20)   DEFAULT 'FAIBLE',
    est_pep             BOOLEAN       NOT NULL DEFAULT FALSE,
    motif_risque_eleve  VARCHAR(500),

    -- TraÃ§abilitÃ©
    verificateur_id     BIGINT        REFERENCES app.utilisateurs(id),
    date_verification   TIMESTAMPTZ,
    date_expiration_kyc DATE,
    observations        TEXT,

    -- ConformitÃ© LBC/FT
    verif_sanctions     BOOLEAN       DEFAULT FALSE,
    verif_listes_noires BOOLEAN       DEFAULT FALSE,
    date_dernier_audit  TIMESTAMPTZ,

    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_kyc_client_imf UNIQUE (imf_id, client_id)
);

-- â”€â”€ Documents KYC â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE IF NOT EXISTS app.kyc_documents (
    id                  BIGSERIAL PRIMARY KEY,
    dossier_id          BIGINT        NOT NULL REFERENCES app.kyc_dossiers(id) ON DELETE CASCADE,
    type_document       VARCHAR(50)   NOT NULL,
    nom_fichier         VARCHAR(300)  NOT NULL,
    chemin_stockage     VARCHAR(1000),
    contenu_base64      TEXT,                   -- stockage temporaire (â†’ S3 en prod)
    taille_octets       BIGINT,
    mime_type           VARCHAR(100),
    date_expiration_doc DATE,
    valide              BOOLEAN       DEFAULT NULL, -- NULL = non encore vÃ©rifiÃ©
    motif_rejet         VARCHAR(500),
    verifie_par_id      BIGINT        REFERENCES app.utilisateurs(id),
    date_verification   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- â”€â”€ Historique des vÃ©rifications (audit trail complet) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE TABLE IF NOT EXISTS app.kyc_verifications (
    id                  BIGSERIAL PRIMARY KEY,
    dossier_id          BIGINT        NOT NULL REFERENCES app.kyc_dossiers(id),
    verificateur_id     BIGINT        REFERENCES app.utilisateurs(id),
    ancien_statut       VARCHAR(30),
    nouveau_statut      VARCHAR(30),
    ancien_niveau       VARCHAR(20),
    nouveau_niveau      VARCHAR(20),
    resultat            VARCHAR(30),
    commentaire         TEXT,
    motif_rejet         VARCHAR(500),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- â”€â”€ Index â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
CREATE INDEX IF NOT EXISTS idx_kyc_dossiers_imf_statut    ON app.kyc_dossiers(imf_id, statut);
CREATE INDEX IF NOT EXISTS idx_kyc_dossiers_imf_niveau    ON app.kyc_dossiers(imf_id, niveau_actuel);
CREATE INDEX IF NOT EXISTS idx_kyc_dossiers_risque        ON app.kyc_dossiers(imf_id, niveau_risque);
CREATE INDEX IF NOT EXISTS idx_kyc_dossiers_pep           ON app.kyc_dossiers(imf_id, est_pep) WHERE est_pep = TRUE;
CREATE INDEX IF NOT EXISTS idx_kyc_dossiers_expiration    ON app.kyc_dossiers(date_expiration_kyc) WHERE date_expiration_kyc IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_kyc_documents_dossier      ON app.kyc_documents(dossier_id);
CREATE INDEX IF NOT EXISTS idx_kyc_verifications_dossier  ON app.kyc_verifications(dossier_id);
