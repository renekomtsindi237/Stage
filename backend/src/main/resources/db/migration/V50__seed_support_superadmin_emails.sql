-- ============================================================
-- V50 — Création du compte SUPPORT
--
-- SUPPORT : support@gmail.com  /  admin123
--
-- Note : SUPER_ADMIN déjà configuré en V31
--   (renekomtsindi7@gmail.com / Mbetoumou olive77)
-- ============================================================

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
