-- ============================================================
-- V13 — Enrichissement workflow recouvrement (réalité camerounaise)
-- COBAC provisionnement, caution solidaire, frais, accord rééchelonnement
-- ============================================================

-- ── Enrichissement de app.dossiers_recouvrement ────────────────────────────
ALTER TABLE app.dossiers_recouvrement
    ADD COLUMN IF NOT EXISTS categorie_cobtac          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS taux_provision             NUMERIC(5,2)  NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS montant_provision          NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS date_premiere_echeance_impayee DATE,
    ADD COLUMN IF NOT EXISTS nom_caution               VARCHAR(200),
    ADD COLUMN IF NOT EXISTS telephone_caution         VARCHAR(30),
    ADD COLUMN IF NOT EXISTS type_garantie             VARCHAR(40),
    ADD COLUMN IF NOT EXISTS frais_recouvrement        NUMERIC(15,2) NOT NULL DEFAULT 0;

-- ── Enrichissement de app.actions_recouvrement ─────────────────────────────
ALTER TABLE app.actions_recouvrement
    ADD COLUMN IF NOT EXISTS frais_engages             NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS statut_verif_momo         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS numero_telephone_paiement VARCHAR(20);

-- ── Accord de rééchelonnement (acte formel) ────────────────────────────────
CREATE TABLE IF NOT EXISTS app.accords_reechelonnement (
    id                          BIGSERIAL PRIMARY KEY,
    dossier_id                  BIGINT NOT NULL REFERENCES app.dossiers_recouvrement(id),
    nouveau_montant_mensuel     NUMERIC(15,2) NOT NULL,
    nombre_nouvelles_echeances  INTEGER       NOT NULL,
    date_debut_nouvel_echeancier DATE         NOT NULL,
    taux_interet_annuel         NUMERIC(5,2),
    approuve_par_id             BIGINT REFERENCES app.utilisateurs(id),
    date_signature              DATE,
    observations                TEXT,
    actif                       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_accord_dossier ON app.accords_reechelonnement(dossier_id);
CREATE INDEX IF NOT EXISTS idx_dossier_categorie_cobtac ON app.dossiers_recouvrement(imf_id, categorie_cobtac);
