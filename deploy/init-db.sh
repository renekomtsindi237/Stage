#!/bin/bash
# Crée la base Airflow si elle n'existe pas (exécuté par postgres au premier démarrage)
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    SELECT 'CREATE DATABASE airflow_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'airflow_db')\gexec
    GRANT ALL PRIVILEGES ON DATABASE airflow_db TO "$POSTGRES_USER";
EOSQL
