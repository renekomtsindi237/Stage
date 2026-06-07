-- ============================================================
-- V12 — Workflow de recouvrement des créances (Cameroun/OHADA)
-- Phases : RELANCE_AMIABLE → MISE_EN_DEMEURE → CONTENTIEUX
--           REECHELONNEMENT, PERTE (radiation)
-- ============================================================

-- Dossier de recouvrement par créance (un dossier par prêt en retard)
CREATE TABLE IF NOT EXISTS app.dossiers_recouvrement (
  id                      BIGSERIAL PRIMARY KEY,
  imf_id                  BIGINT NOT NULL REFERENCES app.imf(id),
  id_pret                 VARCHAR(100) NOT NULL,
  nom_client              VARCHAR(200),
  montant_impaye          NUMERIC(15,2) NOT NULL DEFAULT 0,
  jours_retard            INTEGER NOT NULL DEFAULT 0,
  phase                   VARCHAR(60) NOT NULL DEFAULT 'RELANCE_AMIABLE',
  date_ouverture          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  date_derniere_action    TIMESTAMPTZ,
  agent_responsable_id    BIGINT REFERENCES app.utilisateurs(id),
  clos                    BOOLEAN NOT NULL DEFAULT FALSE,
  date_cloture            TIMESTAMPTZ,
  motif_cloture           VARCHAR(300),
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Actions individuelles enregistrées dans chaque dossier
CREATE TABLE IF NOT EXISTS app.actions_recouvrement (
  id                      BIGSERIAL PRIMARY KEY,
  dossier_id              BIGINT NOT NULL REFERENCES app.dossiers_recouvrement(id),
  type_action             VARCHAR(60) NOT NULL,
  date_action             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  agent_id                BIGINT REFERENCES app.utilisateurs(id),
  resultat                VARCHAR(60),
  promesse_date           DATE,
  promesse_montant        NUMERIC(15,2),
  canal_paiement          VARCHAR(30),
  reference_transaction   VARCHAR(100),
  observation             TEXT,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dossier_imf_pret  ON app.dossiers_recouvrement(imf_id, id_pret);
CREATE INDEX IF NOT EXISTS idx_dossier_imf_phase ON app.dossiers_recouvrement(imf_id, phase);
CREATE INDEX IF NOT EXISTS idx_dossier_imf_clos  ON app.dossiers_recouvrement(imf_id, clos);
CREATE INDEX IF NOT EXISTS idx_action_dossier    ON app.actions_recouvrement(dossier_id);
CREATE INDEX IF NOT EXISTS idx_action_type       ON app.actions_recouvrement(type_action);
