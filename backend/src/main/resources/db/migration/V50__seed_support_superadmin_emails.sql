-- ============================================================
-- V50 — Création du compte SUPPORT
--
-- SUPPORT : support@gmail.com  /  admin123
--
-- Note : SUPER_ADMIN déjà configuré en V31
--   (renekomtsindi7@gmail.com / Mbetoumou olive77)
-- ============================================================

-- 1. Étendre la contrainte role_check pour inclure tous les rôles de l'enum
ALTER TABLE app.utilisateurs DROP CONSTRAINT IF EXISTS utilisateurs_role_check;
ALTER TABLE app.utilisateurs
    ADD CONSTRAINT utilisateurs_role_check
    CHECK (role IN (
        'SUPER_ADMIN',
        'DIRECTEUR',
        'RESPONSABLE_RECOUVREMENT',
        'ANALYSTE',
        'DSI',
        'SUPPORT',
        'AGENT',
        'AGENT_CREDIT',
        'CHEF_AGENCE',
        'ANALYSTE_ENGAGEMENTS',
        'AGENT_SAISIE',
        'CAISSIER'
    ));

-- 2. Créer le compte SUPPORT (cross-IMF, imf_id NULL)
INSERT INTO app.utilisateurs (username, email, password_hash, role, actif, imf_id)
VALUES (
    'support',
    'support@gmail.com',
    '$2b$10$55ZszcvF4Xzr6Eve4zj2p.HGtwXOSwLI977TUDJr48vndpDSOZTiy',
    'SUPPORT',
    TRUE,
    NULL
)
ON CONFLICT (username) DO UPDATE
    SET email         = EXCLUDED.email,
        password_hash = EXCLUDED.password_hash,
        actif         = TRUE;
