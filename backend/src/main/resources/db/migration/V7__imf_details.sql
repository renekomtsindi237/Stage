-- ============================================================
-- V7__imf_details.sql
-- Extension de la table app.imf avec les informations
-- de constitution, paramètres métier et données techniques
-- pour l'onboarding complet d'une IMF.
-- ============================================================

ALTER TABLE app.imf
    -- ── Identité & constitution ─────────────────────────────
    ADD COLUMN IF NOT EXISTS denomination_sociale VARCHAR(200),
    ADD COLUMN IF NOT EXISTS adresse_siege        VARCHAR(500),
    ADD COLUMN IF NOT EXISTS forme_juridique      VARCHAR(50),
    ADD COLUMN IF NOT EXISTS capital_social       NUMERIC(20,2),
    ADD COLUMN IF NOT EXISTS num_agrement         VARCHAR(100),
    ADD COLUMN IF NOT EXISTS telephone            VARCHAR(20),
    ADD COLUMN IF NOT EXISTS email                VARCHAR(100),

    -- ── Paramètres crédit ──────────────────────────────────
    ADD COLUMN IF NOT EXISTS taux_interet_annuel   NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS duree_max_credit_mois INTEGER,
    ADD COLUMN IF NOT EXISTS taux_penalite_retard  NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS seuil_relance_jours   INTEGER,

    -- ── Paramètres épargne ─────────────────────────────────
    ADD COLUMN IF NOT EXISTS taux_epargne          NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS solde_min_epargne     NUMERIC(15,2),
    ADD COLUMN IF NOT EXISTS frais_tenue_compte    NUMERIC(10,2),

    -- ── Segmentation & garanties ───────────────────────────
    ADD COLUMN IF NOT EXISTS segments_clients  VARCHAR(200),
    ADD COLUMN IF NOT EXISTS types_garanties   VARCHAR(200);

COMMENT ON COLUMN app.imf.denomination_sociale   IS 'Raison sociale officielle';
COMMENT ON COLUMN app.imf.adresse_siege          IS 'Adresse physique du siège social';
COMMENT ON COLUMN app.imf.forme_juridique        IS 'Forme juridique : SA, SARL, Coopérative, Mutuelle, Association';
COMMENT ON COLUMN app.imf.capital_social         IS 'Montant total du capital social en FCFA';
COMMENT ON COLUMN app.imf.num_agrement           IS 'Numéro d''agrément COBAC';
COMMENT ON COLUMN app.imf.taux_interet_annuel    IS 'Taux d''intérêt annuel sur crédits (%)';
COMMENT ON COLUMN app.imf.duree_max_credit_mois  IS 'Durée maximale d''un crédit en mois';
COMMENT ON COLUMN app.imf.taux_penalite_retard   IS 'Taux de pénalité de retard (%/mois)';
COMMENT ON COLUMN app.imf.seuil_relance_jours    IS 'Nombre de jours avant première relance';
COMMENT ON COLUMN app.imf.taux_epargne           IS 'Taux de rémunération de l''épargne (%)';
COMMENT ON COLUMN app.imf.solde_min_epargne      IS 'Solde minimum requis pour un compte épargne (FCFA)';
COMMENT ON COLUMN app.imf.frais_tenue_compte     IS 'Frais mensuels de tenue de compte (FCFA)';
COMMENT ON COLUMN app.imf.segments_clients       IS 'Segments clients acceptés, séparés par virgule';
COMMENT ON COLUMN app.imf.types_garanties        IS 'Types de garanties acceptées, séparés par virgule';
