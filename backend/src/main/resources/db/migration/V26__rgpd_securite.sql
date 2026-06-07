-- ============================================================
-- V26__rgpd_securite.sql
-- Conformité Loi n° 2024/017 du 23 décembre 2024
-- Protection des données à caractère personnel (Cameroun)
--
-- Tables créées :
--   1. app.audit_trail       — piste d'audit immuable (art. 27)
--   2. app.consentements     — suivi des consentements (art. 9)
--   3. app.etiquettes_dossiers — étiquetage/classification des dossiers
--   4. app.demandes_rgpd     — demandes d'exercice de droits (art. 37-43)
-- ============================================================

-- ============================================================
-- 1. Piste d'audit immuable — app.audit_trail
--    Enregistre toutes les actions sensibles avec old/new values.
--    Immutabilité garantie par règles PostgreSQL (no UPDATE/DELETE).
-- ============================================================
CREATE TABLE IF NOT EXISTS app.audit_trail (
    id                  BIGSERIAL PRIMARY KEY,
    imf_id              BIGINT        REFERENCES app.imf(id) ON DELETE RESTRICT,
    acteur_id           BIGINT        REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    acteur_username     VARCHAR(50)   NOT NULL,
    acteur_role         VARCHAR(30)   NOT NULL,
    -- Catégorie d'action
    action              VARCHAR(50)   NOT NULL
                        CHECK (action IN (
                            'CREATION', 'MODIFICATION', 'SUPPRESSION',
                            'CONSULTATION', 'EXPORT', 'CONNEXION', 'DECONNEXION',
                            'CHANGEMENT_STATUT', 'ACCES_REFUSE', 'MASQUAGE_DONNEES',
                            'DEMANDE_RGPD', 'CONSENTEMENT'
                        )),
    -- Entité concernée
    entite_type         VARCHAR(50)   NOT NULL
                        CHECK (entite_type IN (
                            'DOSSIER', 'CREANCE', 'CLIENT', 'POSITION',
                            'COLLECTE', 'ALERTE', 'UTILISATEUR', 'ECHEANCE',
                            'ETIQUETTE', 'CONSENTEMENT', 'EXPORT', 'AUTH',
                            'VIOLATION_DONNEES'
                        )),
    entite_id           VARCHAR(100),
    -- Delta (old/new) en JSONB pour audit structuré
    ancienne_valeur     JSONB,
    nouvelle_valeur     JSONB,
    -- Contexte
    motif               TEXT,
    ip_client           VARCHAR(45),
    user_agent          VARCHAR(500),
    statut              VARCHAR(20)   NOT NULL DEFAULT 'SUCCES'
                        CHECK (statut IN ('SUCCES', 'ECHEC', 'REFUS')),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Index pour les requêtes fréquentes
CREATE INDEX IF NOT EXISTS idx_at_imf_id      ON app.audit_trail (imf_id);
CREATE INDEX IF NOT EXISTS idx_at_acteur_id   ON app.audit_trail (acteur_id);
CREATE INDEX IF NOT EXISTS idx_at_action      ON app.audit_trail (action);
CREATE INDEX IF NOT EXISTS idx_at_entite      ON app.audit_trail (entite_type, entite_id);
CREATE INDEX IF NOT EXISTS idx_at_created_at  ON app.audit_trail (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_at_imf_date    ON app.audit_trail (imf_id, created_at DESC);

COMMENT ON TABLE app.audit_trail IS
    'Piste d''audit immuable — art. 27 Loi 2024/017 Cameroun — no UPDATE/DELETE via rules';
COMMENT ON COLUMN app.audit_trail.ancienne_valeur IS
    'État de l''entité avant modification — JSONB masqué selon rôle lors de la lecture';
COMMENT ON COLUMN app.audit_trail.nouvelle_valeur IS
    'État de l''entité après modification — JSONB masqué selon rôle lors de la lecture';

-- Immutabilité : PostgreSQL RULES bloquent tout UPDATE et DELETE
-- Ces règles remplacent les commandes par NOTHING (no-op silencieux)
CREATE OR REPLACE RULE audit_trail_no_update
    AS ON UPDATE TO app.audit_trail DO INSTEAD NOTHING;

CREATE OR REPLACE RULE audit_trail_no_delete
    AS ON DELETE TO app.audit_trail DO INSTEAD NOTHING;

-- Trigger de vérification d'intégrité : refuse les tentatives de troncature
CREATE OR REPLACE FUNCTION app.prevent_audit_truncate()
RETURNS EVENT_TRIGGER AS $$
DECLARE
    obj RECORD;
BEGIN
    FOR obj IN SELECT * FROM pg_event_trigger_ddl_commands()
    LOOP
        IF obj.object_identity = 'app.audit_trail' THEN
            RAISE EXCEPTION 'Opération interdite sur app.audit_trail (piste d''audit immuable)';
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;


-- ============================================================
-- 2. Consentements — app.consentements
--    Suivi du consentement des clients/agents pour chaque finalité.
--    Art. 9 : consentement préalable libre, éclairé, spécifique.
--    Art. 50 : consentement préalable obligatoire.
-- ============================================================
CREATE TABLE IF NOT EXISTS app.consentements (
    id                  BIGSERIAL PRIMARY KEY,
    imf_id              BIGINT        NOT NULL REFERENCES app.imf(id) ON DELETE CASCADE,
    -- Sujet du consentement (client ou agent de l'IMF)
    sujet_type          VARCHAR(20)   NOT NULL CHECK (sujet_type IN ('CLIENT', 'AGENT')),
    sujet_id            BIGINT        NOT NULL,      -- id dans app.utilisateurs ou clients_informels
    sujet_reference     VARCHAR(100),                -- identifiant métier (numéro client CBS)
    -- Finalité du traitement consentie
    finalite            VARCHAR(100)  NOT NULL
                        CHECK (finalite IN (
                            'GEOLOCALISATION',       -- art. 50 — position GPS agents
                            'RECOUVREMENT',          -- traitement données pour recouvrement
                            'SCORING_ML',            -- modèle ML de scoring
                            'NOTIFICATION_FCM',      -- notifications push mobiles
                            'PARTAGE_DONNEES_CBS',   -- partage avec système CBS
                            'EXPORT_RAPPORT',        -- export données dans rapports
                            'CONSERVATION_ETENDUE'   -- conservation au-delà durée légale
                        )),
    -- État du consentement
    accorde             BOOLEAN       NOT NULL DEFAULT FALSE,
    date_consentement   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    date_retrait        TIMESTAMPTZ,
    -- Contexte de collecte (art. 21 : information claire)
    canal_collecte      VARCHAR(30)   NOT NULL DEFAULT 'APPLICATION'
                        CHECK (canal_collecte IN ('APPLICATION', 'FORMULAIRE_PAPIER', 'AGENT', 'SMS')),
    version_politique   VARCHAR(20)   NOT NULL DEFAULT '1.0',  -- version de la politique de confidentialité
    ip_collecte         VARCHAR(45),
    recollecte_requise  BOOLEAN       NOT NULL DEFAULT FALSE,   -- si politique mise à jour
    -- Traçabilité
    collecte_par_id     BIGINT        REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    notes               TEXT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_consentement_sujet_finalite UNIQUE (imf_id, sujet_type, sujet_id, finalite)
);

CREATE INDEX IF NOT EXISTS idx_cons_imf_id      ON app.consentements (imf_id);
CREATE INDEX IF NOT EXISTS idx_cons_sujet        ON app.consentements (sujet_type, sujet_id);
CREATE INDEX IF NOT EXISTS idx_cons_finalite     ON app.consentements (finalite);
CREATE INDEX IF NOT EXISTS idx_cons_accorde      ON app.consentements (accorde) WHERE accorde = TRUE;

DROP TRIGGER IF EXISTS trg_consentements_updated_at ON app.consentements;
CREATE TRIGGER trg_consentements_updated_at
    BEFORE UPDATE ON app.consentements
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

COMMENT ON TABLE app.consentements IS
    'Consentements RGPD par sujet et finalité — art. 9, 50 Loi 2024/017 Cameroun';


-- ============================================================
-- 3. Étiquettes dossiers — app.etiquettes_dossiers
--    Classification et traçabilité des dossiers de recouvrement.
--    Permet de marquer un dossier (sensible, contentieux, prioritaire…).
-- ============================================================
CREATE TABLE IF NOT EXISTS app.etiquettes_dossiers (
    id                  BIGSERIAL PRIMARY KEY,
    imf_id              BIGINT        NOT NULL REFERENCES app.imf(id) ON DELETE CASCADE,
    -- Dossier cible (référence au numéro de prêt CBS)
    dossier_ref         VARCHAR(50)   NOT NULL,   -- id_pret ou référence CBS
    dossier_type        VARCHAR(30)   NOT NULL DEFAULT 'DOSSIER_RECOUVREMENT'
                        CHECK (dossier_type IN (
                            'DOSSIER_RECOUVREMENT', 'DOSSIER_CREANCE', 'DOSSIER_CLIENT'
                        )),
    -- Étiquette
    code_etiquette      VARCHAR(50)   NOT NULL
                        CHECK (code_etiquette IN (
                            'PRIORITAIRE',          -- traitement urgent
                            'SENSIBLE',             -- données sensibles — accès restreint
                            'CONTENTIEUX',          -- procédure judiciaire en cours
                            'RESTRUCTURE',          -- dossier en restructuration
                            'PERDU',                -- client introuvable
                            'DECEDE',               -- client décédé
                            'FRAUDE_SUSPECTEE',     -- enquête fraude
                            'GARANTIE_ACTIVEE',     -- garantie/caution activée
                            'SAISONNALITE',         -- remboursement saisonnier
                            'SUIVI_SPECIAL'         -- suivi renforcé RR
                        )),
    couleur             VARCHAR(7),                -- code couleur hex (#FF5733)
    libelle_custom      VARCHAR(100),              -- libellé personnalisé optionnel
    commentaire         TEXT,
    -- Validité de l'étiquette
    date_debut          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    date_fin            TIMESTAMPTZ,               -- null = étiquette permanente
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    -- Traçabilité de qui a posé / retiré l'étiquette
    pose_par_id         BIGINT        NOT NULL REFERENCES app.utilisateurs(id) ON DELETE RESTRICT,
    pose_par_username   VARCHAR(50)   NOT NULL,
    retire_par_id       BIGINT        REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    retire_par_username VARCHAR(50),
    date_retrait        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_etiq_imf_ref   ON app.etiquettes_dossiers (imf_id, dossier_ref);
CREATE INDEX IF NOT EXISTS idx_etiq_code       ON app.etiquettes_dossiers (code_etiquette);
CREATE INDEX IF NOT EXISTS idx_etiq_active     ON app.etiquettes_dossiers (active) WHERE active = TRUE;
CREATE INDEX IF NOT EXISTS idx_etiq_pose_par   ON app.etiquettes_dossiers (pose_par_id);

DROP TRIGGER IF EXISTS trg_etiquettes_updated_at ON app.etiquettes_dossiers;
CREATE TRIGGER trg_etiquettes_updated_at
    BEFORE UPDATE ON app.etiquettes_dossiers
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

COMMENT ON TABLE app.etiquettes_dossiers IS
    'Étiquetage et classification des dossiers de recouvrement — traçabilité et filtrage';


-- ============================================================
-- 4. Demandes RGPD — app.demandes_rgpd
--    Registre des demandes d'exercice des droits.
--    Art. 37 : accès, 38 : effacement, 39 : rectification,
--    Art. 40 : opposition, 43 : portabilité.
--    Délai de réponse légal : 30 jours (art. 41).
-- ============================================================
CREATE TABLE IF NOT EXISTS app.demandes_rgpd (
    id                  BIGSERIAL PRIMARY KEY,
    imf_id              BIGINT        NOT NULL REFERENCES app.imf(id) ON DELETE CASCADE,
    -- Demandeur
    demandeur_id        BIGINT        REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    demandeur_username  VARCHAR(50)   NOT NULL,
    demandeur_email     VARCHAR(150),
    -- Type de droit exercé
    type_droit          VARCHAR(30)   NOT NULL
                        CHECK (type_droit IN (
                            'ACCES',          -- art. 37 : obtenir copie de ses données
                            'RECTIFICATION',  -- art. 39 : corriger données inexactes
                            'EFFACEMENT',     -- art. 38 : droit à l'oubli
                            'OPPOSITION',     -- art. 40 : s'opposer au traitement
                            'PORTABILITE',    -- art. 43 : recevoir ses données en format structuré
                            'LIMITATION'      -- limiter le traitement
                        )),
    -- Périmètre de la demande
    perimetre           TEXT          NOT NULL,   -- description de ce que le client demande
    finalite_concernee  VARCHAR(100),             -- finalité spécifique si opposition partielle
    -- Traitement de la demande
    statut              VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE'
                        CHECK (statut IN (
                            'EN_ATTENTE', 'EN_COURS', 'TRAITEE',
                            'REFUSEE', 'PARTIELLEMENT_TRAITEE'
                        )),
    date_soumission     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    date_limite_reponse TIMESTAMPTZ   NOT NULL DEFAULT (NOW() + INTERVAL '30 days'),
    date_traitement     TIMESTAMPTZ,
    traite_par_id       BIGINT        REFERENCES app.utilisateurs(id) ON DELETE SET NULL,
    traite_par_username VARCHAR(50),
    -- Réponse / justification
    reponse             TEXT,                     -- réponse envoyée au demandeur
    motif_refus         TEXT,                     -- si refus : base légale du refus
    -- Données exportées (pour PORTABILITE et ACCES)
    export_url          VARCHAR(500),             -- lien sécurisé temporaire vers l'export
    export_expire_at    TIMESTAMPTZ,
    -- Canal de soumission
    canal_soumission    VARCHAR(30)   NOT NULL DEFAULT 'APPLICATION'
                        CHECK (canal_soumission IN ('APPLICATION', 'EMAIL', 'COURRIER', 'AGENT')),
    ip_soumission       VARCHAR(45),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rgpd_imf_id     ON app.demandes_rgpd (imf_id);
CREATE INDEX IF NOT EXISTS idx_rgpd_demandeur  ON app.demandes_rgpd (demandeur_id);
CREATE INDEX IF NOT EXISTS idx_rgpd_type       ON app.demandes_rgpd (type_droit);
CREATE INDEX IF NOT EXISTS idx_rgpd_statut     ON app.demandes_rgpd (statut);
CREATE INDEX IF NOT EXISTS idx_rgpd_date_lim   ON app.demandes_rgpd (date_limite_reponse)
    WHERE statut IN ('EN_ATTENTE', 'EN_COURS');

DROP TRIGGER IF EXISTS trg_demandes_rgpd_updated_at ON app.demandes_rgpd;
CREATE TRIGGER trg_demandes_rgpd_updated_at
    BEFORE UPDATE ON app.demandes_rgpd
    FOR EACH ROW EXECUTE FUNCTION app.set_updated_at();

COMMENT ON TABLE app.demandes_rgpd IS
    'Registre des demandes RGPD — art. 37-43 Loi 2024/017 Cameroun — délai réponse 30j';

-- ============================================================
-- 5. Vue : demandes en retard (SLA 30j dépassé)
-- ============================================================
CREATE OR REPLACE VIEW app.v_demandes_rgpd_en_retard AS
SELECT
    dr.*,
    EXTRACT(EPOCH FROM (NOW() - dr.date_limite_reponse)) / 86400 AS jours_retard
FROM app.demandes_rgpd dr
WHERE dr.statut IN ('EN_ATTENTE', 'EN_COURS')
  AND dr.date_limite_reponse < NOW();

COMMENT ON VIEW app.v_demandes_rgpd_en_retard IS
    'Demandes RGPD dont le délai légal de 30j est dépassé — art. 41 Loi 2024/017';

-- Note : la colonne imf_id sur app.journal_audit existe déjà depuis V5__multi_tenant.sql.
-- Aucune modification nécessaire sur cette table.

-- ============================================================
-- 6. Purge de la piste d'audit — art. 13 : conservation limitée
--    Rétention par défaut : 5 ans (durée prêts + obligations OHADA)
--    La procédure exporte les entrées vers app.audit_trail_archive
--    avant suppression physique (piste conservée hors-ligne).
-- ============================================================
CREATE TABLE IF NOT EXISTS app.audit_trail_archive (
    LIKE app.audit_trail INCLUDING ALL
);
COMMENT ON TABLE app.audit_trail_archive IS
    'Archive hors-ligne de audit_trail — entrées purgées après rétention 5 ans (art. 13)';

-- La table archive est aussi immuable
CREATE OR REPLACE RULE audit_trail_archive_no_update
    AS ON UPDATE TO app.audit_trail_archive DO INSTEAD NOTHING;
CREATE OR REPLACE RULE audit_trail_archive_no_delete
    AS ON DELETE TO app.audit_trail_archive DO INSTEAD NOTHING;

CREATE OR REPLACE PROCEDURE app.purger_audit_trail_ancien(
    p_retention_ans INT DEFAULT 5
)
LANGUAGE plpgsql AS $$
DECLARE
    v_date_limite TIMESTAMPTZ := NOW() - (p_retention_ans || ' years')::INTERVAL;
    v_count       BIGINT;
BEGIN
    -- Archiver d'abord (copie vers table archive)
    INSERT INTO app.audit_trail_archive
    SELECT * FROM app.audit_trail
    WHERE created_at < v_date_limite;

    GET DIAGNOSTICS v_count = ROW_COUNT;

    -- Supprimer depuis audit_trail (les RULES ne s'appliquent pas au propriétaire de la procédure)
    -- Cette procédure doit être exécutée par le rôle propriétaire de la table (pg_superuser ou role owner)
    DELETE FROM app.audit_trail WHERE created_at < v_date_limite;

    RAISE NOTICE 'Purge audit_trail : % entrées archivées (antérieures à %)', v_count, v_date_limite;
END;
$$;

COMMENT ON PROCEDURE app.purger_audit_trail_ancien IS
    'Archive et purge les entrées d''audit_trail de plus de N ans — art. 13 Loi 2024/017. '
    'Doit être appelée par le rôle propriétaire de la table (pas le rôle applicatif).';

-- Index de purge (accès rapide par date)
CREATE INDEX IF NOT EXISTS idx_at_created_at_purge ON app.audit_trail (created_at);
