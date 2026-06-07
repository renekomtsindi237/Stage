-- ============================================================
-- V23 — Schéma ML : MCRS (Multi-Criteria Recovery Scoring)
-- Features, scores, explainability SHAP, tracking MLflow-like
-- ============================================================

CREATE SCHEMA IF NOT EXISTS ml;

-- ── Features client par période (input du modèle MCRS) ──────────────────────
-- Calculé par le pipeline DAG ml_feature_engineering
CREATE TABLE IF NOT EXISTS ml.features_client (
    id                          BIGSERIAL PRIMARY KEY,
    imf_id                      BIGINT      NOT NULL,
    client_id_externe           VARCHAR(50) NOT NULL,
    periode_debut               DATE        NOT NULL,
    periode_fin                 DATE        NOT NULL,
    -- Comportement collecte épargne
    nb_collectes_12m            INTEGER     NOT NULL DEFAULT 0,
    montant_total_collectes_12m NUMERIC(15,2) NOT NULL DEFAULT 0,
    regularite_collecte_pct     NUMERIC(5,4),               -- 0-1, ponctualité sur cycles
    montant_moy_collecte        NUMERIC(12,2),
    ecart_type_collecte         NUMERIC(12,2),              -- stabilité
    tendance_collecte_3m        NUMERIC(8,4),               -- pente régression linéaire
    nb_cycles_manques_12m       SMALLINT    NOT NULL DEFAULT 0,
    -- Comportement remboursement crédit
    nb_remboursements_12m       INTEGER     NOT NULL DEFAULT 0,
    taux_remboursement_pct      NUMERIC(5,4),
    jours_retard_moyen          NUMERIC(8,2),
    jours_retard_max            INTEGER,
    nb_incidents_paiement       SMALLINT    NOT NULL DEFAULT 0,
    montant_impaye_courant      NUMERIC(15,2) NOT NULL DEFAULT 0,
    -- Profil client informel
    anciennete_client_jours     INTEGER,
    secteur_principal           VARCHAR(30),
    nb_produits_vendus          SMALLINT    NOT NULL DEFAULT 0,
    revenu_mensuel_estime       NUMERIC(12,2),
    -- Facteurs externes (moyennes sur la période)
    prix_produit_principal_moy  NUMERIC(12,4),              -- prix moyen du produit principal
    volatilite_prix_produit     NUMERIC(8,4),               -- écart-type prix
    tendance_prix_30j           NUMERIC(8,4),               -- pente prix sur 30j
    precipitation_moy_mm        NUMERIC(8,2),               -- météo zone client
    indice_secheresse_max       VARCHAR(20),
    inflation_mensuelle_moy     NUMERIC(6,4),
    taux_directeur_beac         NUMERIC(6,4),
    nb_evenements_negatifs      SMALLINT    NOT NULL DEFAULT 0,
    -- Features géospatiales
    distance_agence_km          NUMERIC(8,2),
    distance_marche_km          NUMERIC(8,2),
    densite_agents_zone         NUMERIC(8,4),
    -- Features dérivées (feature engineering)
    ratio_collecte_credit       NUMERIC(8,4),               -- collecte/crédit
    capacite_remboursement      NUMERIC(12,2),              -- revenu - dépenses estimées
    indice_resilience           NUMERIC(5,4),               -- 0-1, diversification activités
    -- Métadonnées calcul
    version_features            VARCHAR(10) NOT NULL DEFAULT 'v1',
    dag_run_id                  TEXT,
    computed_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (imf_id, client_id_externe, periode_debut, version_features)
);

CREATE INDEX IF NOT EXISTS idx_feat_imf_client  ON ml.features_client(imf_id, client_id_externe);
CREATE INDEX IF NOT EXISTS idx_feat_periode      ON ml.features_client(periode_debut, periode_fin);
CREATE INDEX IF NOT EXISTS idx_feat_version      ON ml.features_client(version_features);
COMMENT ON TABLE ml.features_client IS 'Feature store MCRS — features comportementales, profil, externes par client et période';

-- ── Scores MCRS (Multi-Criteria Recovery Scoring) ──────────────────────────
CREATE TABLE IF NOT EXISTS ml.client_scores (
    id                          BIGSERIAL PRIMARY KEY,
    imf_id                      BIGINT      NOT NULL,
    client_id_externe           VARCHAR(50) NOT NULL,
    feature_id                  BIGINT      REFERENCES ml.features_client(id),
    model_run_id                BIGINT,                     -- FK vers ml.model_runs
    -- Scores composites MCRS
    score_crs                   NUMERIC(5,4) NOT NULL,      -- Collection Reliability Score [0,1]
    score_rps                   NUMERIC(5,4) NOT NULL,      -- Recovery Prediction Score [0,1]
    score_csi                   NUMERIC(5,4) NOT NULL,      -- Client Solvency Index [0,1]
    score_mcrs                  NUMERIC(5,4) NOT NULL,      -- composite = f(CRS, RPS, CSI)
    -- Classification risque
    classe_risque               VARCHAR(10) NOT NULL
                                CHECK (classe_risque IN ('FAIBLE','MODERE','ELEVE','CRITIQUE')),
    probabilite_defaut_30j      NUMERIC(5,4),               -- P(défaut dans 30 jours)
    probabilite_defaut_90j      NUMERIC(5,4),               -- P(défaut dans 90 jours)
    -- Intervalles de confiance
    score_mcrs_ic_bas           NUMERIC(5,4),
    score_mcrs_ic_haut          NUMERIC(5,4),
    -- Temps de survie estimé (analyse de survie Cox)
    temps_survie_median_jours   INTEGER,
    -- Recommandations
    action_recommandee          VARCHAR(50)
                                CHECK (action_recommandee IN (
                                    'AUCUNE','RELANCE_PREVENTIVE','VISITE_TERRAIN',
                                    'RESTRUCTURATION','MISE_EN_DEMEURE','ESCALADE_JURIDIQUE'
                                )),
    priorite_recouvrement       SMALLINT CHECK (priorite_recouvrement BETWEEN 1 AND 5),
    -- Métadonnées
    scored_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valide_jusqu_au             DATE,
    UNIQUE (imf_id, client_id_externe, scored_at)
);

CREATE INDEX IF NOT EXISTS idx_score_imf_client ON ml.client_scores(imf_id, client_id_externe);
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='ml' AND table_name='client_scores' AND column_name='classe_risque') THEN
        CREATE INDEX IF NOT EXISTS idx_score_risque ON ml.client_scores(imf_id, classe_risque);
    ELSIF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='ml' AND table_name='client_scores' AND column_name='niveau_risque') THEN
        CREATE INDEX IF NOT EXISTS idx_score_risque ON ml.client_scores(imf_id, niveau_risque);
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_score_at          ON ml.client_scores(scored_at DESC);
COMMENT ON TABLE ml.client_scores IS 'Scores MCRS par client — CRS+RPS+CSI composite, classification risque, recommandations';

-- ── SHAP — explicabilité des scores ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ml.shap_explanations (
    id              BIGSERIAL PRIMARY KEY,
    score_id        BIGINT NOT NULL REFERENCES ml.client_scores(id) ON DELETE CASCADE,
    feature_name    VARCHAR(100) NOT NULL,
    shap_value      NUMERIC(12,6) NOT NULL,
    feature_value   TEXT,                                   -- valeur brute de la feature
    rang_importance SMALLINT NOT NULL,
    signe           CHAR(1) CHECK (signe IN ('+','-')),     -- impact positif ou négatif
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shap_score   ON ml.shap_explanations(score_id);
CREATE INDEX IF NOT EXISTS idx_shap_feature ON ml.shap_explanations(feature_name);
COMMENT ON TABLE ml.shap_explanations IS 'Valeurs SHAP par feature — explicabilité des scores MCRS pour les directeurs IMF';

-- ── Suivi des runs MLflow-like ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ml.model_runs (
    id                  BIGSERIAL PRIMARY KEY,
    model_name          VARCHAR(50) NOT NULL DEFAULT 'MCRS_XGBoost',
    version             VARCHAR(20) NOT NULL,
    dag_run_id          TEXT,
    -- Hyperparamètres (JSON flexible)
    params_json         JSONB NOT NULL DEFAULT '{}',
    -- Métriques de validation (walk-forward temporelle)
    auc_roc             NUMERIC(6,4),
    precision_score     NUMERIC(6,4),
    recall_score        NUMERIC(6,4),
    f1_score            NUMERIC(6,4),
    gini_coefficient    NUMERIC(6,4),
    ks_statistic        NUMERIC(6,4),
    brier_score         NUMERIC(6,4),
    -- Validation croisée temporelle
    nb_folds_temporels  SMALLINT,
    periode_train_debut DATE,
    periode_train_fin   DATE,
    periode_test_debut  DATE,
    periode_test_fin    DATE,
    -- Statut
    statut              VARCHAR(20) NOT NULL DEFAULT 'EN_COURS'
                        CHECK (statut IN ('EN_COURS','SUCCES','ECHEC','DEGRADE')),
    est_modele_actif    BOOLEAN NOT NULL DEFAULT FALSE,
    artifact_path       VARCHAR(500),                       -- chemin vers le modèle sérialisé
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_run_modele_actif ON ml.model_runs(est_modele_actif);
COMMENT ON TABLE ml.model_runs IS 'Tracking MLflow-like des runs MCRS — hyperparamètres, métriques walk-forward, versions';

-- Lien FK après création de ml.model_runs (idempotent)
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ml.client_scores'::regclass
          AND conname = 'fk_score_model_run'
    ) THEN
        ALTER TABLE ml.client_scores
            ADD CONSTRAINT fk_score_model_run
            FOREIGN KEY (model_run_id) REFERENCES ml.model_runs(id);
    END IF;
END $$;

-- ── Alertes prédictives (générées par le modèle ML) ─────────────────────────
CREATE TABLE IF NOT EXISTS ml.alertes_predictives (
    id                  BIGSERIAL PRIMARY KEY,
    imf_id              BIGINT      NOT NULL,
    client_id_externe   VARCHAR(50) NOT NULL,
    score_id            BIGINT      REFERENCES ml.client_scores(id),
    type_alerte         VARCHAR(40) NOT NULL
                        CHECK (type_alerte IN (
                            'RISQUE_DEFAUT_IMMINENT',
                            'BAISSE_COLLECTE_DETECTEE',
                            'TENDANCE_NEGATIVE_PROLONGEE',
                            'RUPTURE_CYCLE_COLLECTE',
                            'DEGRADATION_SCORE_RAPIDE',
                            'FACTEUR_EXTERNE_CRITIQUE',
                            'PROMESSE_ROMPUE',
                            'CIBLE_RECOUVREMENT_PRIORITAIRE'
                        )),
    urgence             VARCHAR(10) NOT NULL DEFAULT 'MOYENNE'
                        CHECK (urgence IN ('BASSE','MOYENNE','HAUTE','CRITIQUE')),
    titre               VARCHAR(200) NOT NULL,
    description         TEXT,
    recommandation      TEXT,
    -- Valeurs déclenchantes
    valeur_declenchante NUMERIC(12,4),
    seuil_alerte        NUMERIC(12,4),
    -- Statut traitement
    statut              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (statut IN ('ACTIVE','EN_TRAITEMENT','RESOLUE','IGNOREE')),
    prise_en_charge_par BIGINT REFERENCES app.utilisateurs(id),
    prise_en_charge_at  TIMESTAMPTZ,
    resolution_note     TEXT,
    -- Notifications
    fcm_sent            BOOLEAN NOT NULL DEFAULT FALSE,
    email_sent          BOOLEAN NOT NULL DEFAULT FALSE,
    sse_sent            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_alerte_imf_statut  ON ml.alertes_predictives(imf_id, statut);
CREATE INDEX IF NOT EXISTS idx_alerte_urgence     ON ml.alertes_predictives(imf_id, urgence);
CREATE INDEX IF NOT EXISTS idx_alerte_created     ON ml.alertes_predictives(created_at DESC);
COMMENT ON TABLE ml.alertes_predictives IS 'Alertes prédictives générées par MCRS — risque défaut, baisse collecte, facteurs externes';
