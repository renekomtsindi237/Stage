-- V43 : corrige la contrainte FK audit_trail_acteur_id_fkey
-- Remplace la FK simple par ON DELETE SET NULL pour permettre la suppression
-- d'un utilisateur tout en conservant l'historique d'audit (acteur_id devient NULL).

ALTER TABLE app.audit_trail
    DROP CONSTRAINT IF EXISTS audit_trail_acteur_id_fkey;

ALTER TABLE app.audit_trail
    ADD CONSTRAINT audit_trail_acteur_id_fkey
        FOREIGN KEY (acteur_id)
            REFERENCES app.utilisateurs (id)
            ON DELETE SET NULL;
