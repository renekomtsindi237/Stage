-- V41 : Correction des types SMALLINT → INTEGER
--
-- Les migrations V35 et V36 avaient défini ces colonnes en SMALLINT.
-- Les entités JPA correspondantes déclarent Integer (→ Types#INTEGER).
-- Hibernate valide le schéma au démarrage et refusait de lancer l'application.
-- PostgreSQL autorise la conversion implicite SMALLINT → INTEGER (widening cast).

ALTER TABLE app.comite_decisions ALTER COLUMN duree_approuvee TYPE INTEGER;
ALTER TABLE app.contrats_credit  ALTER COLUMN nb_echeances    TYPE INTEGER;
ALTER TABLE app.dossiers_credit  ALTER COLUMN duree_mois      TYPE INTEGER;
ALTER TABLE app.plans_apurement  ALTER COLUMN nb_echeances    TYPE INTEGER;
