"""
conftest.py — Configuration pytest partagée pour les tests du pipeline.

Crée un fichier .env de test dans sys.path pour éviter de charger
les variables d'environnement réelles en CI.
"""

import os
import sys

import pytest

# Ajoute le répertoire racine du pipeline au path Python
# pour que les imports fonctionnent sans installation via pip
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))


@pytest.fixture(autouse=True)
def set_test_env(monkeypatch):
    """
    Injecte les variables d'environnement de test avant chaque test.
    Empêche les tests de se connecter à une DB ou API réelle.
    """
    env_vars = {
        "POSTGRES_HOST": "localhost",
        "POSTGRES_PORT": "5432",
        "POSTGRES_DB": "imf_test_db",
        "POSTGRES_USER": "imf_test_user",
        "POSTGRES_PASSWORD": "test_password",
        "STAGING_SCHEMA": "staging",
        "DW_SCHEMA": "dw",
        "APP_SCHEMA": "app",
        "SPRING_BASE_URL": "http://localhost:8080",
        "SPRING_API_KEY": "test_internal_api_key",
        "APP_ENV": "test",
        "ALERTE_MIN_JOURS_RETARD": "30",
        "PAR30_DAYS": "30",
        "PAR90_DAYS": "90",
    }
    for key, value in env_vars.items():
        monkeypatch.setenv(key, value)
