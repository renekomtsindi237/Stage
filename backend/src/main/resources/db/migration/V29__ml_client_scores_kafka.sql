-- ============================================================
-- V29 — Adaptation ml.client_scores pour intégration Kafka
--
-- La table V23 utilisait classe_risque + unique(imf_id, client_id_externe, scored_at)
-- → un enregistrement par scoring, ce qui empêche l'upsert Kafka.
-- On migre vers un enregistrement unique par client (score le plus récent).
-- ============================================================

-- 1. Renommer classe_risque → niveau_risque (vocabulaire MCRS standardisé)
ALTER TABLE ml.client_scores RENAME COLUMN classe_risque TO niveau_risque;

-- 2. Ajouter les colonnes Kafka / COBAC absentes de V23
ALTER TABLE ml.client_scores
    ADD COLUMN IF NOT EXISTS cobac_classe         VARCHAR(5)
                             CHECK (cobac_classe IN ('A','B','C','D','E')),
    ADD COLUMN IF NOT EXISTS cobac_provision_taux NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS model_version        VARCHAR(20) DEFAULT '1.0.0',
    ADD COLUMN IF NOT EXISTS updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 3. Remplir cobac_classe depuis jours_retard si possible
--    (fallback à 'A' pour les lignes sans retard documenté)
UPDATE ml.client_scores SET cobac_classe = 'A' WHERE cobac_classe IS NULL;

-- 4. Remplir cobac_provision_taux cohérent avec cobac_classe
UPDATE ml.client_scores
SET cobac_provision_taux = CASE cobac_classe
    WHEN 'A' THEN 0.00
    WHEN 'B' THEN 0.20
    WHEN 'C' THEN 0.50
    WHEN 'D' THEN 0.80
    WHEN 'E' THEN 1.00
    ELSE 0.00
END
WHERE cobac_provision_taux IS NULL;

-- 5. Supprimer l'ancienne contrainte d'unicité temporelle
--    (elle empêchait l'upsert "latest score wins")
ALTER TABLE ml.client_scores
    DROP CONSTRAINT IF EXISTS client_scores_imf_id_client_id_externe_scored_at_key;

-- 6. Ajouter la contrainte d'unicité par client (un score courant par client/IMF)
--    Avec ON CONFLICT pour l'upsert Kafka
ALTER TABLE ml.client_scores
    ADD CONSTRAINT client_scores_client_imf_unique
    UNIQUE (client_id_externe, imf_id);

-- 7. Table de détail des alertes (elementCollection JPA)
CREATE TABLE IF NOT EXISTS ml.client_score_alertes (
    client_score_id BIGINT NOT NULL REFERENCES ml.client_scores(id) ON DELETE CASCADE,
    alerte          VARCHAR(100) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_csa_score_id ON ml.client_score_alertes(client_score_id);

COMMENT ON TABLE ml.client_scores IS 'Score MCRS courant par client — upsert Kafka, un enregistrement par (client_id_externe, imf_id)';
COMMENT ON COLUMN ml.client_scores.niveau_risque IS 'FAIBLE | MODERE | ELEVE | CRITIQUE (anciennement classe_risque)';
COMMENT ON COLUMN ml.client_scores.cobac_classe IS 'Classification COBAC EMF 01/02 CEMAC : A(<30j) B(30-89j) C(90-179j) D(180-359j) E(360j+)';
