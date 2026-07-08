-- ============================================================
-- V60 — Miroir des scores MCRS sur app.creances
--
-- pipeline/dags/scripts/ml_scoring_utils.py écrivait depuis l'origine vers ces
-- colonnes (score_mcrs, score_crs, score_rps, score_csi, classe_risque_mcrs)
-- sur app.creances, mais elles n'avaient jamais été migrées — le scoring
-- batch n'a donc jamais pu s'exécuter contre la vraie base (colonne
-- inexistante). Ajout idempotent, nullable, sans effet tant que le pipeline
-- ne les remplit pas.
-- ============================================================

ALTER TABLE app.creances
    ADD COLUMN IF NOT EXISTS score_mcrs         NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS score_crs          NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS score_rps          NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS score_csi          NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS classe_risque_mcrs VARCHAR(10)
                             CHECK (classe_risque_mcrs IN ('FAIBLE','MODERE','ELEVE','CRITIQUE'));

COMMENT ON COLUMN app.creances.score_mcrs IS 'Miroir du dernier score MCRS (ml.client_scores) pour requêtes de dashboard sans jointure';
