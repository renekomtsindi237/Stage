-- ============================================================
-- V47 — FINANCE SARL : données ML réelles
--   · Création ml.drift_metrics + ml.feature_drift
--   · PSI 12 mois + contributions features
--   · app.alertes_impayes pour PAR clients CLF021-CLF030
-- ============================================================

-- ── Schémas tables ML manquants ──────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ml.drift_metrics (
    id              BIGSERIAL PRIMARY KEY,
    model_run_id    BIGINT,
    modele_version  VARCHAR(20)    NOT NULL DEFAULT 'MCRS-v2.4.1',
    imf_id          BIGINT,
    psi_global      NUMERIC(6,4)   NOT NULL,
    statut_derive   VARCHAR(20)    NOT NULL DEFAULT 'STABLE'
                    CHECK (statut_derive IN ('STABLE','DERIVE','CRITIQUE')),
    calculated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ml.feature_drift (
    id              BIGSERIAL PRIMARY KEY,
    model_run_id    BIGINT,
    modele_version  VARCHAR(20)    NOT NULL DEFAULT 'MCRS-v2.4.1',
    imf_id          BIGINT,
    nom_metier      VARCHAR(100)   NOT NULL,
    nom_technique   VARCHAR(100)   NOT NULL,
    psi             NUMERIC(6,4)   NOT NULL,
    contribution    NUMERIC(6,4)   NOT NULL,
    calculated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

DO $$
DECLARE
    v_imf_id   BIGINT;
    v_run_id   BIGINT;
BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';
    IF v_imf_id IS NULL THEN RETURN; END IF;

    SELECT id INTO v_run_id FROM ml.model_runs
    WHERE model_name = 'MCRS_XGBoost' AND est_modele_actif = TRUE LIMIT 1;

    -- ── 1. Métriques de dérive PSI — 12 mois ─────────────────────────────────
    INSERT INTO ml.drift_metrics
        (model_run_id, modele_version, imf_id, psi_global, statut_derive, calculated_at)
    SELECT v_run_id, 'MCRS-v2.4.1', v_imf_id, vals.psi,
           CASE WHEN vals.psi >= 0.20 THEN 'DERIVE' ELSE 'STABLE' END, vals.dt
    FROM (VALUES
        (0.082::NUMERIC, '2025-06-28 02:00:00'::TIMESTAMPTZ),
        (0.094,          '2025-07-28 02:00:00'),
        (0.108,          '2025-08-28 02:00:00'),
        (0.115,          '2025-09-28 02:00:00'),
        (0.131,          '2025-10-28 02:00:00'),
        (0.147,          '2025-11-28 02:00:00'),
        (0.163,          '2025-12-28 02:00:00'),
        (0.198,          '2026-01-28 02:00:00'),
        (0.211,          '2026-02-28 02:00:00'),
        (0.196,          '2026-03-28 02:00:00'),
        (0.207,          '2026-04-28 02:00:00'),
        (0.219,          '2026-05-28 02:00:00')
    ) AS vals(psi, dt)
    WHERE NOT EXISTS (
        SELECT 1 FROM ml.drift_metrics dm
        WHERE dm.imf_id = v_imf_id
          AND DATE_TRUNC('month', dm.calculated_at) = DATE_TRUNC('month', vals.dt)
    );

    -- ── 2. Contributions features ─────────────────────────────────────────────
    INSERT INTO ml.feature_drift
        (model_run_id, modele_version, imf_id,
         nom_metier, nom_technique, psi, contribution, calculated_at)
    SELECT v_run_id, 'MCRS-v2.4.1', v_imf_id,
           vals.nom_m, vals.nom_t, vals.psi, vals.contrib, NOW()
    FROM (VALUES
        ('Historique remboursement', 'taux_remboursement_pct',      0.312::NUMERIC, 0.285::NUMERIC),
        ('Ratio dette/revenu',       'ratio_collecte_credit',        0.247,          0.225),
        ('Ancienneté compte',        'anciennete_client_jours',      0.178,          0.162),
        ('Montant collectes',        'montant_total_collectes_12m',  0.143,          0.130),
        ('Secteur activité',         'secteur_principal',            0.089,          0.081)
    ) AS vals(nom_m, nom_t, psi, contrib)
    WHERE NOT EXISTS (
        SELECT 1 FROM ml.feature_drift fd
        WHERE fd.imf_id = v_imf_id AND fd.nom_technique = vals.nom_t
    );

    -- ── 3. Alertes impayes — PAR30+ (CLF021-CLF027) ──────────────────────────
    INSERT INTO app.alertes_impayes
        (id_pret, imf_id, jours_retard, montant_en_retard, statut_alerte)
    SELECT vals.id_pret, v_imf_id, vals.jours, vals.montant, 'ACTIVE'
    FROM (VALUES
        ('CLF021', 38, 126000.00::NUMERIC),
        ('CLF022', 45, 218000.00),
        ('CLF023', 35, 148000.00),
        ('CLF024', 62, 188000.00),
        ('CLF025', 41, 158000.00),
        ('CLF026', 37, 172000.00),
        ('CLF027', 55, 108000.00)
    ) AS vals(id_pret, jours, montant)
    WHERE NOT EXISTS (
        SELECT 1 FROM app.alertes_impayes ai
        WHERE ai.id_pret = vals.id_pret AND ai.statut_alerte = 'ACTIVE'
    );

    -- ── 4. Alertes impayes — PAR90 critiques (CLF028-CLF030) ────────────────
    INSERT INTO app.alertes_impayes
        (id_pret, imf_id, jours_retard, montant_en_retard, statut_alerte)
    SELECT vals.id_pret, v_imf_id, vals.jours, vals.montant, 'ESCALADEE'
    FROM (VALUES
        ('CLF028', 96, 556500.00::NUMERIC),
        ('CLF029', 96, 760500.00),
        ('CLF030', 96, 675000.00)
    ) AS vals(id_pret, jours, montant)
    WHERE NOT EXISTS (
        SELECT 1 FROM app.alertes_impayes ai
        WHERE ai.id_pret = vals.id_pret
    );

    RAISE NOTICE 'V47 OK — FINANCE SARL : drift_metrics + feature_drift + alertes_impayes créés';
END $$;
