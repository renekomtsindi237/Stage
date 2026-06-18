-- V44 : soft-delete pour les utilisateurs
-- Ajoute un flag `supprime` permettant d'anonymiser un compte sans supprimer
-- la ligne (évite les violations FK des tables d'audit, collectes, recouvrement...).

ALTER TABLE app.utilisateurs
    ADD COLUMN IF NOT EXISTS supprime BOOLEAN NOT NULL DEFAULT FALSE;
