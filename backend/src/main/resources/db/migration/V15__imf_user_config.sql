-- V15 : Paramètres opérationnels adaptables par IMF + préférences utilisateur
-- Chaque IMF peut désormais configurer ses propres limites KYC, niveau requis et
-- seuil de connexion. Chaque utilisateur gère sa langue et ses notifications.

-- ── Table imf ──────────────────────────────────────────────────────────────────

ALTER TABLE app.imf
    ADD COLUMN IF NOT EXISTS max_document_kyc_octets  BIGINT      NOT NULL DEFAULT 5242880,
    ADD COLUMN IF NOT EXISTS niveau_kyc_minimal        VARCHAR(20) NOT NULL DEFAULT 'NIVEAU_1',
    ADD COLUMN IF NOT EXISTS max_tentatives_connexion  INTEGER     NOT NULL DEFAULT 5;

COMMENT ON COLUMN app.imf.max_document_kyc_octets  IS 'Taille max d''un document KYC en octets (défaut : 5 Mo)';
COMMENT ON COLUMN app.imf.niveau_kyc_minimal        IS 'Niveau KYC minimal obligatoire pour accorder un crédit (COBAC)';
COMMENT ON COLUMN app.imf.max_tentatives_connexion  IS 'Max tentatives de connexion par IP avant blocage temporaire';

-- ── Table utilisateurs ─────────────────────────────────────────────────────────

ALTER TABLE app.utilisateurs
    ADD COLUMN IF NOT EXISTS pref_langue           VARCHAR(5) NOT NULL DEFAULT 'fr',
    ADD COLUMN IF NOT EXISTS notifications_actives  BOOLEAN    NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN app.utilisateurs.pref_langue          IS 'Langue préférée : fr ou en';
COMMENT ON COLUMN app.utilisateurs.notifications_actives IS 'Activer/désactiver les notifications SSE et FCM';
