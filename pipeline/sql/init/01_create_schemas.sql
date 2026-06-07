-- ============================================================
-- 01_create_schemas.sql
-- Création des schémas PostgreSQL de la plateforme IMF
-- Ordre : raw → staging → dw → reporting
-- ============================================================

-- Schéma raw : données sources brutes, non modifiées
CREATE SCHEMA IF NOT EXISTS raw;
COMMENT ON SCHEMA raw IS 'Données sources brutes ingérées sans transformation (MTN, Orange, CBS export)';

-- Schéma staging : données nettoyées et normalisées par dbt
CREATE SCHEMA IF NOT EXISTS staging;
COMMENT ON SCHEMA staging IS 'Données nettoyées, typage corrigé, doublons supprimés';

-- Schéma dw : Data Warehouse en étoile (faits + dimensions)
CREATE SCHEMA IF NOT EXISTS dw;
COMMENT ON SCHEMA dw IS 'Data Warehouse en étoile — faits et dimensions pour analyse';

-- Schéma reporting : vues agrégées prêtes pour Superset / API
CREATE SCHEMA IF NOT EXISTS reporting;
COMMENT ON SCHEMA reporting IS 'Vues agrégées et matérialisées pour Superset et API Spring Boot';

-- Schéma app : données applicatives Spring Boot (hors pipeline)
CREATE SCHEMA IF NOT EXISTS app;
COMMENT ON SCHEMA app IS 'Données applicatives gérées par Spring Boot (utilisateurs, alertes)';
