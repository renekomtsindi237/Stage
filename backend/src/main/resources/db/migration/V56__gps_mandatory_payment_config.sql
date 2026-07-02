-- ============================================================
-- V56 — GPS obligatoire + config paiement mobile money
--
-- 1. Ajoute gps_obligatoire à app.imf
--    (TRUE pour FINANCE SARL → position non désactivable par l'agent)
-- 2. Crée app.imf_payment_config pour Orange Money + MTN MoMo
--    (secrets chiffrés AES-256-GCM côté applicatif)
-- 3. FINANCE SARL : active gps_obligatoire, accorde le consentement
--    de géolocalisation à l'agent existant (renekomtsindi99),
--    seed sa position initiale (Yaoundé Nlongkak)
-- ============================================================

-- ── 1. Flag GPS obligatoire sur l'IMF ────────────────────────────────────────
ALTER TABLE app.imf
    ADD COLUMN IF NOT EXISTS gps_obligatoire BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN app.imf.gps_obligatoire IS
    'TRUE = la géolocalisation est obligatoire pour tous les agents de cette IMF '
    '(désactivation bloquée même via DELETE /agents/me/position)';

-- ── 2. Table de configuration des paiements mobiles ──────────────────────────
CREATE TABLE IF NOT EXISTS app.imf_payment_config (
    id              BIGSERIAL   PRIMARY KEY,
    imf_id          BIGINT      NOT NULL UNIQUE REFERENCES app.imf(id) ON DELETE CASCADE,

    -- ── MTN Mobile Money (CAMTEL/MTN Cameroun) ───────────────────────────────
    mtn_actif                               BOOLEAN     NOT NULL DEFAULT FALSE,
    mtn_base_url                            VARCHAR(200) DEFAULT 'https://sandbox.momodeveloper.mtn.com',
    mtn_environment                         VARCHAR(20)  DEFAULT 'sandbox'
                                                CHECK (mtn_environment IN ('sandbox', 'production')),
    mtn_api_user                            VARCHAR(100),
    -- Chiffré AES-256-GCM (ApiKeyEncryptionService)
    mtn_api_key_encrypted                   TEXT,
    -- Clé d'abonnement Collection (encaissements clients)
    mtn_subscription_key_collection_masked  VARCHAR(20),   -- 8 premiers chars + '...'
    mtn_subscription_key_collection_enc     TEXT,
    -- Clé d'abonnement Disbursement (décaissements)
    mtn_subscription_key_disbursement_masked VARCHAR(20),
    mtn_subscription_key_disbursement_enc   TEXT,
    -- Callback MoMo (URL publique backend → reçoit les notifications)
    mtn_callback_url                        VARCHAR(500),

    -- ── Orange Money (Orange Cameroun) ───────────────────────────────────────
    orange_actif                            BOOLEAN     NOT NULL DEFAULT FALSE,
    orange_base_url                         VARCHAR(200) DEFAULT 'https://api.orange.com/orange-money-webpay/cm/v1',
    orange_environment                      VARCHAR(20)  DEFAULT 'sandbox'
                                                CHECK (orange_environment IN ('sandbox', 'production')),
    orange_merchant_key_masked              VARCHAR(20),
    orange_merchant_key_enc                 TEXT,
    orange_client_id                        VARCHAR(100),
    orange_client_secret_masked             VARCHAR(20),
    orange_client_secret_enc                TEXT,
    -- Code marchand OM
    orange_merchant_code                    VARCHAR(50),
    orange_return_url                       VARCHAR(500),
    orange_cancel_url                       VARCHAR(500),
    orange_notif_url                        VARCHAR(500),

    -- ── Audit ─────────────────────────────────────────────────────────────────
    updated_by_username                     VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  app.imf_payment_config               IS 'Credentials Mobile Money par IMF — secrets chiffrés AES-256-GCM';
COMMENT ON COLUMN app.imf_payment_config.mtn_api_key_encrypted IS 'Clé API MTN MoMo chiffrée AES-256-GCM (Base64)';
COMMENT ON COLUMN app.imf_payment_config.orange_merchant_key_enc IS 'Merchant Key Orange Money chiffrée AES-256-GCM (Base64)';

-- ── 3. FINANCE SARL : GPS obligatoire + consentement agent + seed position ───
DO $$
DECLARE
    v_imf_id   BIGINT;
    v_agent_id BIGINT;
BEGIN
    SELECT id INTO v_imf_id FROM app.imf WHERE code = 'FINANCE';
    IF v_imf_id IS NULL THEN
        RAISE NOTICE 'V56 : FINANCE SARL introuvable — skip seed';
        RETURN;
    END IF;

    -- 3a. Activer GPS obligatoire pour FINANCE SARL
    UPDATE app.imf SET gps_obligatoire = TRUE WHERE id = v_imf_id;

    -- 3b. Récupérer l'agent terrain existant
    SELECT id INTO v_agent_id FROM app.utilisateurs
        WHERE email = 'renekomtsindi99@gmail.com' AND imf_id = v_imf_id LIMIT 1;

    IF v_agent_id IS NULL THEN
        RAISE NOTICE 'V56 : agent renekomtsindi99 introuvable — skip consentement';
        RETURN;
    END IF;

    -- 3c. Accorder le consentement de géolocalisation (DSI déjà accordé)
    INSERT INTO app.consentements
        (imf_id, sujet_type, sujet_id, sujet_reference,
         finalite, accorde, date_consentement, created_at, updated_at)
    SELECT v_imf_id, 'AGENT', v_agent_id, 'renekomtsindi99',
           'GEOLOCALISATION', TRUE, NOW(), NOW(), NOW()
    WHERE NOT EXISTS (
        SELECT 1 FROM app.consentements
        WHERE imf_id     = v_imf_id
          AND sujet_type = 'AGENT'
          AND sujet_id   = v_agent_id
          AND finalite   = 'GEOLOCALISATION'
    );

    -- 3d. Seed dernière position (Yaoundé Nlongkak — siège FINANCE SARL)
    UPDATE app.utilisateurs
    SET latitude             = 3.8480,
        longitude            = 11.5021,
        precision_gps_m      = 12.5,
        derniere_position_at = NOW() - INTERVAL '8 minutes',
        position_active      = TRUE,
        updated_at           = NOW()
    WHERE id = v_agent_id
      AND latitude IS NULL;

    -- 3e. Seed historique positions (trajet de la journée)
    INSERT INTO app.positions_agents
        (imf_id, agent_id, latitude, longitude, precision_gps_m,
         vitesse_kmh, source, captured_at)
    SELECT v_imf_id, v_agent_id,
           3.8480 + (pts.i * 0.0008),
           11.5021 + (pts.i * 0.0005),
           10.0 + pts.i,
           CASE WHEN pts.i < 3 THEN 0 ELSE 15.0 + (pts.i * 2) END,
           'MOBILE',
           NOW() - ((10 - pts.i) * INTERVAL '12 minutes')
    FROM generate_series(0, 9) AS pts(i)
    WHERE NOT EXISTS (
        SELECT 1 FROM app.positions_agents
        WHERE agent_id = v_agent_id
          AND captured_at::DATE = CURRENT_DATE
        LIMIT 1
    );

    -- 3f. Créer une config paiement vide pour FINANCE SARL (placeholder prêt à configurer)
    INSERT INTO app.imf_payment_config (imf_id)
    VALUES (v_imf_id)
    ON CONFLICT (imf_id) DO NOTHING;

    RAISE NOTICE 'V56 OK — FINANCE SARL gps_obligatoire=TRUE, consentement agent=%, seed position=OK',
        v_agent_id;
END $$;
