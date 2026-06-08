-- ============================================================
-- V31__update_admin_credentials.sql
-- Mise à jour des credentials du SUPER_ADMIN initial :
--   email         : renekomtsindi7@gmail.com
--   password      : Mbetoumou olive77  (BCrypt cost 10)
-- ============================================================

UPDATE app.utilisateurs
SET email                = 'renekomtsindi7@gmail.com',
    password_hash        = '$2a$10$bS0Sa7oNk129i/dHr0WAV.f9la0lZWkRro/zcjFBfKOG./cU94rEW',
    must_change_password = FALSE,
    updated_at           = NOW()
WHERE role     = 'SUPER_ADMIN'
  AND username = 'admin';
