-- ============================================================
-- V22 — Créances enrichies pour recouvrement
-- Extension des dossiers existants + table creances centrale
-- Lien avec profil client informel et scoring MCRS
-- ============================================================

-- ── Table créances (entité centrale de recouvrement) ───────────────────────
-- Représente une créance en cours de recouvrement, liée à un prêt CBS externe
CREATE TABLE IF NOT EXISTS app.creances (
    id                          BIGSERIAL PRIMARY KEY,
    imf_id                      BIGINT       NOT NULL REFERENCES app.imf(id),
    agence_id                   BIGINT       REFERENCES app.agences(id),
    id_pret_externe             VARCHAR(100) NOT NULL,      -- référence CBS
    client_id_externe           VARCHAR(50)  NOT NULL,
    client_informel_id          BIGINT       REFERENCES app.clients_informels(id),
    -- Montants
    montant_initial             NUMERIC(15,2) NOT NULL CHECK (montant_initial > 0),
    montant_impaye              NUMERIC(15,2) NOT NULL CHECK (montant_impaye >= 0),
    capital_restant_du          NUMERIC(15,2),
    interets_retard             NUMERIC(15,2) NOT NULL DEFAULT 0,
    penalites                   NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Temporalité
    date_deblocage              DATE,
    date_premiere_echeance      DATE,
    date_premiere_echeance_impayee DATE,
    date_ouverture_creance      DATE NOT NULL DEFAULT CURRENT_DATE,
    -- Classification COBAC / PAR
    jours_retard                INTEGER NOT NULL DEFAULT 0 CHECK (jours_retard >= 0),
    categorie_par               VARCHAR(10) NOT NULL DEFAULT 'COURANT'
                                CHECK (categorie_par IN ('COURANT','PAR30','PAR60','PAR90','PAR180','PERTE')),
    -- COBAC provisionnement (CEMAC Règlement 01/02/CEMAC/UMAC/COBAC)
    classe_risque_cobac         VARCHAR(10)
                                CHECK (classe_risque_cobac IN ('A','B','C','D','E')),
    taux_provision_cobac        NUMERIC(5,2) NOT NULL DEFAULT 0,
    montant_provision           NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Garanties
    type_garantie               VARCHAR(40),
    valeur_garantie             NUMERIC(15,2),
    nom_caution                 VARCHAR(200),
    telephone_caution           VARCHAR(20),
    -- Statut
    statut                      VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                                CHECK (statut IN (
                                    'ACTIVE','RECOUVREMENT_AMIABLE',
                                    'MISE_EN_DEMEURE','CONTENTIEUX',
                                    'REECHELONNEE','SOLDEE','IRRECOVERABLE','RADIEE'
                                )),
    agent_responsable_id        BIGINT REFERENCES app.utilisateurs(id),
    dossier_recouvrement_id     BIGINT REFERENCES app.dossiers_recouvrement(id),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (imf_id, id_pret_externe)
);

CREATE INDEX IF NOT EXISTS idx_creance_imf_statut    ON app.creances(imf_id, statut);
CREATE INDEX IF NOT EXISTS idx_creance_imf_par       ON app.creances(imf_id, categorie_par);
CREATE INDEX IF NOT EXISTS idx_creance_client        ON app.creances(imf_id, client_id_externe);
CREATE INDEX IF NOT EXISTS idx_creance_jours_retard  ON app.creances(jours_retard);
CREATE INDEX IF NOT EXISTS idx_creance_agent         ON app.creances(agent_responsable_id);
COMMENT ON TABLE app.creances IS 'Créances en recouvrement — entité centrale avec classification COBAC, provisions et lien CBS';

-- ── Enrichissement dossiers_recouvrement — lien créance ─────────────────────
ALTER TABLE app.dossiers_recouvrement
    ADD COLUMN IF NOT EXISTS creance_id BIGINT REFERENCES app.creances(id),
    ADD COLUMN IF NOT EXISTS client_informel_id BIGINT REFERENCES app.clients_informels(id),
    ADD COLUMN IF NOT EXISTS priorite_scoring NUMERIC(5,2);

CREATE INDEX IF NOT EXISTS idx_dr_creance_id ON app.dossiers_recouvrement(creance_id);

-- ── Promesses de paiement (suivi formel) ────────────────────────────────────
CREATE TABLE IF NOT EXISTS app.promesses_paiement (
    id                      BIGSERIAL PRIMARY KEY,
    creance_id              BIGINT NOT NULL REFERENCES app.creances(id),
    action_recouvrement_id  BIGINT REFERENCES app.actions_recouvrement(id),
    agent_id                BIGINT NOT NULL REFERENCES app.utilisateurs(id),
    date_promesse           DATE NOT NULL,
    montant_promis          NUMERIC(15,2) NOT NULL,
    date_echeance_promesse  DATE NOT NULL,
    statut                  VARCHAR(20) NOT NULL DEFAULT 'EN_ATTENTE'
                            CHECK (statut IN ('EN_ATTENTE','HONOREE','PARTIELLEMENT_HONOREE','ROMPUE','ANNULEE')),
    montant_recu            NUMERIC(15,2) NOT NULL DEFAULT 0,
    date_reglement          DATE,
    nb_relances_envoyees    SMALLINT NOT NULL DEFAULT 0,
    observation             TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pp_creance   ON app.promesses_paiement(creance_id);
CREATE INDEX IF NOT EXISTS idx_pp_statut    ON app.promesses_paiement(statut, date_echeance_promesse);
COMMENT ON TABLE app.promesses_paiement IS 'Promesses de paiement formelles — suivi des engagements client lors du recouvrement';

-- ── KPI snapshots recouvrement (calculés par le pipeline) ───────────────────
CREATE TABLE IF NOT EXISTS app.kpi_recouvrement_snapshots (
    id                      BIGSERIAL PRIMARY KEY,
    imf_id                  BIGINT      NOT NULL REFERENCES app.imf(id),
    agence_id               BIGINT      REFERENCES app.agences(id),
    date_calcul             DATE        NOT NULL,
    periode                 VARCHAR(20) NOT NULL DEFAULT 'MENSUEL',
    -- PAR metrics
    par30_montant           NUMERIC(15,2) NOT NULL DEFAULT 0,
    par60_montant           NUMERIC(15,2) NOT NULL DEFAULT 0,
    par90_montant           NUMERIC(15,2) NOT NULL DEFAULT 0,
    par30_taux_pct          NUMERIC(7,4),
    par60_taux_pct          NUMERIC(7,4),
    par90_taux_pct          NUMERIC(7,4),
    -- Recouvrement
    taux_recouvrement_pct   NUMERIC(7,4),
    montant_recouvre        NUMERIC(15,2) NOT NULL DEFAULT 0,
    montant_perte_nette     NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Portefeuille
    encours_total           NUMERIC(15,2) NOT NULL DEFAULT 0,
    nb_creances_actives     INTEGER NOT NULL DEFAULT 0,
    nb_creances_probleme    INTEGER NOT NULL DEFAULT 0,
    -- Provisions COBAC
    total_provisions        NUMERIC(15,2) NOT NULL DEFAULT 0,
    dag_run_id              TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (imf_id, agence_id, date_calcul, periode)
);

CREATE INDEX IF NOT EXISTS idx_kpi_rec_imf_date ON app.kpi_recouvrement_snapshots(imf_id, date_calcul);
COMMENT ON TABLE app.kpi_recouvrement_snapshots IS 'Snapshots périodiques KPI recouvrement — PAR, taux recouvrement, provisions COBAC';
