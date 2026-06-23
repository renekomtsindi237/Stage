-- ============================================================
-- V50 — Credentials SUPER_ADMIN et SUPPORT
--
-- SUPER_ADMIN :  admin@microrecouv.cm  /  Admin2026!
-- SUPPORT     :  support@microrecouv.cm  /  Support2026!
--
-- Les hash BCrypt ($2b$10$...) sont compatibles avec Spring
-- Security BCryptPasswordEncoder (vérifie $2a$ et $2b$).
-- ============================================================

-- 1. Mettre à jour le SUPER_ADMIN existant avec un email
UPDATE app.utilisateurs
SET email = 'admin@microrecouv.cm',
    password_hash = '$2b$10$Oc.n1ZiZ.VlPgv1VuxgMDu13RlnVaj4qFJXibAETEhhYSACS9lTh6'
WHERE username = 'admin'
  AND role = 'SUPER_ADMIN';

-- 2. Créer le compte SUPPORT (cross-IMF, imf_id NULL)
INSERT INTO app.utilisateurs (username, email, password_hash, role, actif, imf_id)
VALUES (
    'support',
    'support@microrecouv.cm',
    '$2b$10$NFll/6xfYbP/B9T85yKFyuQ3OdAdxXd0y8PZ26geZJK.ipNQ5ZHwi',
    'SUPPORT',
    TRUE,
    NULL
)
ON CONFLICT (username) DO UPDATE
    SET email         = EXCLUDED.email,
        password_hash = EXCLUDED.password_hash,
        actif         = TRUE;

-- S'assurer qu'il n'y a pas de conflit sur l'email non plus
-- (si la colonne email a une contrainte UNIQUE)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'app'
          AND table_name   = 'utilisateurs'
          AND constraint_type = 'UNIQUE'
    ) THEN
        -- Supprimer les doublons email éventuels avant l'insert
        DELETE FROM app.utilisateurs
        WHERE email IN ('admin@microrecouv.cm', 'support@microrecouv.cm')
          AND username NOT IN ('admin', 'support');
    END IF;
END $$;
