-- ============================================================
-- V1__init_app_schema.sql
-- Schéma app — Tables gérées par Spring Boot
-- Flyway migration initiale
-- ============================================================

CREATE SCHEMA IF NOT EXISTS app;

-- ------------------------------------------------------------
-- Utilisateurs
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app.utilisateurs (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(30)  NOT NULL
                    CHECK (role IN ('SUPER_ADMIN','DIRECTEUR','RESPONSABLE_RECOUVREMENT','ANALYSTE','DSI','AGENT')),
    fcm_token       VARCHAR(500),
    zone_id         VARCHAR(20),
    actif           BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login      TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE app.utilisateurs IS 'Comptes utilisateurs de la plateforme';

-- ------------------------------------------------------------
-- Refresh tokens JWT
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app.refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app.utilisateurs(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rt_user_id ON app.refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_rt_expires_at ON app.refresh_tokens (expires_at);

COMMENT ON TABLE app.refresh_tokens IS 'Tokens JWT de rafraîchissement (stockage côté serveur)';

-- ------------------------------------------------------------
-- Collectes terrain (saisies via l'app Flutter)
-- Données reçues via POST /api/collectes
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app.collectes_terrain (
    id                      BIGSERIAL PRIMARY KEY,
    id_collecte_mobile      VARCHAR(50)  NOT NULL UNIQUE,
    agent_id                BIGINT       NOT NULL REFERENCES app.utilisateurs(id),
    client_id               VARCHAR(30)  NOT NULL,
    pret_id                 VARCHAR(30)  NOT NULL,
    date_collecte           DATE         NOT NULL,
    montant_collecte        NUMERIC(15,2) NOT NULL CHECK (montant_collecte > 0),
    canal_paiement          VARCHAR(20)  NOT NULL
                            CHECK (canal_paiement IN ('MTN','ORANGE','ESPECES','VIREMENT')),
    reference_transaction   VARCHAR(100),
    observation             TEXT,
    statut                  VARCHAR(20)  NOT NULL DEFAULT 'SOUMISE'
                            CHECK (statut IN ('SOUMISE','CONFIRMEE','DOUBLON','REJETEE')),
    latitude                NUMERIC(10,7),
    longitude               NUMERIC(10,7),
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ct_agent_id ON app.collectes_terrain (agent_id);
CREATE INDEX IF NOT EXISTS idx_ct_pret_id  ON app.collectes_terrain (pret_id);
CREATE INDEX IF NOT EXISTS idx_ct_date     ON app.collectes_terrain (date_collecte);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ct_ref_date
    ON app.collectes_terrain (reference_transaction, date_collecte)
    WHERE reference_transaction IS NOT NULL;

COMMENT ON TABLE app.collectes_terrain IS 'Collectes terrain reçues depuis l''app Flutter';

-- ------------------------------------------------------------
-- Alertes impayés (mirror du schéma staging — géré par pipeline)
-- Spring lit et met à jour le statut ; le pipeline insère
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app.alertes_impayes (
    id                  BIGSERIAL PRIMARY KEY,
    id_pret             VARCHAR(30)   NOT NULL,
    date_generation     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    jours_retard        INTEGER       NOT NULL CHECK (jours_retard > 0),
    montant_en_retard   NUMERIC(15,2) NOT NULL CHECK (montant_en_retard > 0),
    statut_alerte       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                        CHECK (statut_alerte IN ('ACTIVE','CLOTUREE','ESCALADEE')),
    fcm_sent            BOOLEAN       NOT NULL DEFAULT FALSE,
    email_sent          BOOLEAN       NOT NULL DEFAULT FALSE,
    date_cloture        TIMESTAMP WITH TIME ZONE,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_alerte_active UNIQUE (id_pret, statut_alerte)
);

CREATE INDEX IF NOT EXISTS idx_ai_pret_id      ON app.alertes_impayes (id_pret);
CREATE INDEX IF NOT EXISTS idx_ai_statut_alerte ON app.alertes_impayes (statut_alerte);

COMMENT ON TABLE app.alertes_impayes IS 'Alertes impayés — insérées par le pipeline, mises à jour par Spring';
