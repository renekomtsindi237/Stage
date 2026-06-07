-- ============================================================
-- V27__violations_donnees.sql
-- Registre des violations de données personnelles
-- Art. 22 — Loi n° 2024/017 du 23 décembre 2024 (Cameroun)
--
-- Délai de notification à l'autorité : 72h après découverte.
-- Notification aux personnes concernées : sans délai injustifié.
-- ============================================================

CREATE TABLE IF NOT EXISTS app.violations_donnees (
    id                      BIGSERIAL PRIMARY KEY,
    imf_id                  BIGINT        NOT NULL REFERENCES app.imf(id) ON DELETE RESTRICT,

    -- Découverte et déclaration
    declarant_id            BIGINT        REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    declarant_username      VARCHAR(50)   NOT NULL,
    date_decouverte         TIMESTAMPTZ   NOT NULL,
    date_declaration        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- Nature de la violation (art. 22 §2)
    type_violation          VARCHAR(50)   NOT NULL
                            CHECK (type_violation IN (
                                'ACCES_NON_AUTORISE',   -- intrusion, vol de session
                                'DIVULGATION_ACCIDENTELLE', -- envoi par erreur
                                'PERTE_DONNEES',         -- suppression accidentelle
                                'MODIFICATION_NON_AUTORISEE', -- altération
                                'RANSOMWARE',            -- chiffrement malveillant
                                'EXFILTRATION',          -- fuite via API ou export
                                'AUTRE'
                            )),
    description             TEXT          NOT NULL,

    -- Périmètre (art. 22 §2b)
    categories_donnees      TEXT          NOT NULL,  -- ex: "positions GPS, noms, numéros de prêt"
    nb_personnes_estimees   INTEGER,                 -- estimation du nombre de personnes touchées
    entites_concernees      TEXT,                    -- tables/systèmes compromis

    -- Sévérité
    severite                VARCHAR(20)   NOT NULL DEFAULT 'MODERE'
                            CHECK (severite IN ('FAIBLE', 'MODERE', 'ELEVE', 'CRITIQUE')),

    -- Mesures prises (art. 22 §2c)
    mesures_immediates      TEXT,                    -- actions d'endiguement
    mesures_correctives     TEXT,                    -- corrections apportées

    -- Statut des notifications réglementaires
    -- Délai légal autorité : 72h (art. 22 §1)
    notif_autorite_requise  BOOLEAN       NOT NULL DEFAULT TRUE,
    notif_autorite_envoyee  BOOLEAN       NOT NULL DEFAULT FALSE,
    notif_autorite_at       TIMESTAMPTZ,
    notif_autorite_ref      VARCHAR(200), -- numéro de dossier auprès de l'autorité

    -- Notification aux personnes concernées
    notif_personnes_requise BOOLEAN       NOT NULL DEFAULT FALSE,
    notif_personnes_envoyee BOOLEAN       NOT NULL DEFAULT FALSE,
    notif_personnes_at      TIMESTAMPTZ,

    -- Statut global
    statut                  VARCHAR(20)   NOT NULL DEFAULT 'DECLAREE'
                            CHECK (statut IN (
                                'DECLAREE',        -- déclarée, mesures en cours
                                'EN_INVESTIGATION', -- enquête en cours
                                'CONTENUE',        -- endiguée, notifications faites
                                'CLOTUREE'         -- clôturée, rapport final soumis
                            )),
    rapport_final           TEXT,                    -- rapport de clôture
    cloture_par_id          BIGINT        REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    cloture_at              TIMESTAMPTZ,

    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_viol_imf_id    ON app.violations_donnees (imf_id);
CREATE INDEX IF NOT EXISTS idx_viol_statut    ON app.violations_donnees (statut);
CREATE INDEX IF NOT EXISTS idx_viol_severite  ON app.violations_donnees (severite);
CREATE INDEX IF NOT EXISTS idx_viol_date_dec  ON app.violations_donnees (date_declaration DESC);

DROP TRIGGER IF EXISTS trg_violations_updated_at ON app.violations_donnees;
CREATE TRIGGER trg_violations_updated_at
    BEFORE UPDATE ON app.violations_donnees
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

-- Vue : violations nécessitant notification autorité dans les 72h
CREATE OR REPLACE VIEW app.v_violations_sla_autorite AS
SELECT
    v.*,
    EXTRACT(EPOCH FROM (v.date_decouverte + INTERVAL '72 hours' - NOW())) / 3600
        AS heures_restantes_autorite,
    (v.date_decouverte + INTERVAL '72 hours') AS deadline_autorite
FROM app.violations_donnees v
WHERE v.notif_autorite_requise  = TRUE
  AND v.notif_autorite_envoyee  = FALSE
  AND v.statut NOT IN ('CLOTUREE');

COMMENT ON TABLE app.violations_donnees IS
    'Registre des violations de données personnelles — art. 22 Loi 2024/017 Cameroun — délai autorité 72h';
COMMENT ON VIEW app.v_violations_sla_autorite IS
    'Violations dont le délai de 72h pour notifier l''autorité n''est pas encore dépassé';
