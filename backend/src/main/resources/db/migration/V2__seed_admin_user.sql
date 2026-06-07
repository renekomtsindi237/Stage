-- ============================================================
-- V2__seed_admin_user.sql
-- Seeding du SUPER_ADMIN initial — seul utilisateur du système
-- Username  : admin
-- Password  : admin123  (BCrypt cost 10)
-- ============================================================

INSERT INTO app.utilisateurs (username, password_hash, role, actif)
VALUES (
    'admin',
    '$2a$10$q2VY3tvpvfCV4R5WRr7BReWFXbyvcnOp3Hv1Y.jTYa7T06bzuDpGW',
    'SUPER_ADMIN',
    TRUE
)
ON CONFLICT (username) DO NOTHING;
