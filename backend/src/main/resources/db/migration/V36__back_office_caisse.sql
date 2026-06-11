-- ============================================================
-- V36 : Back-office crédit et caisse
--
--   app.contrats_credit   — contrat formel généré après décision comité APPROUVE
--   app.decaissements     — exécution du décaissement par le caissier
--   app.operations_caisse — journal complet des mouvements de caisse (débit/crédit)
-- ============================================================

-- ── app.contrats_credit ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.contrats_credit (
    id                  BIGSERIAL    NOT NULL,
    uid                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    dossier_id          BIGINT       NOT NULL UNIQUE REFERENCES app.dossiers_credit(id),
    reference_contrat   VARCHAR(50)  NOT NULL UNIQUE,
    date_signature      DATE,
    montant_final       NUMERIC(15,2) NOT NULL,
    taux_interet        NUMERIC(6,4) NOT NULL,
    frais_dossier       NUMERIC(12,2),
    nb_echeances        SMALLINT     NOT NULL,
    periodicite         VARCHAR(20)  NOT NULL DEFAULT 'MENSUEL',
    signatures_conformes BOOLEAN     NOT NULL DEFAULT FALSE,
    agent_saisie_id     BIGINT       NOT NULL,
    date_generation     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    url_contrat_pdf     VARCHAR(500),
    statut              VARCHAR(20)  NOT NULL DEFAULT 'REDIGE',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_contrats_credit PRIMARY KEY (id),
    CONSTRAINT uq_contrats_credit_uid UNIQUE (uid)
);

CREATE INDEX IF NOT EXISTS idx_contrats_credit_dossier_id
    ON app.contrats_credit (dossier_id);

-- ── app.decaissements ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.decaissements (
    id                  BIGSERIAL    NOT NULL,
    uid                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    contrat_id          BIGINT       NOT NULL UNIQUE REFERENCES app.contrats_credit(id),
    caissier_id         BIGINT       NOT NULL,
    montant_net         NUMERIC(15,2) NOT NULL,
    mode                VARCHAR(20)  NOT NULL,
    reference_paiement  VARCHAR(100),
    date_decaissement   TIMESTAMPTZ,
    autorise_par_id     BIGINT,
    statut              VARCHAR(20)  NOT NULL DEFAULT 'EN_ATTENTE',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_decaissements PRIMARY KEY (id),
    CONSTRAINT uq_decaissements_uid UNIQUE (uid)
);

-- ── app.operations_caisse ────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.operations_caisse (
    id              BIGSERIAL    NOT NULL,
    caissier_id     BIGINT       NOT NULL,
    imf_id          BIGINT       NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    montant         NUMERIC(15,2) NOT NULL,
    reference       VARCHAR(100) NOT NULL,
    pret_id         VARCHAR(100),
    client_id       VARCHAR(100),
    date_operation  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    solde_avant     NUMERIC(18,2),
    solde_apres     NUMERIC(18,2),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_operations_caisse PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_operations_caisse_imf_id
    ON app.operations_caisse (imf_id);
CREATE INDEX IF NOT EXISTS idx_operations_caisse_caissier_id
    ON app.operations_caisse (caissier_id);
CREATE INDEX IF NOT EXISTS idx_operations_caisse_date
    ON app.operations_caisse (date_operation DESC);
