"""
test_api_client.py — Tests du client HTTP SpringAPIClient.

Utilise la bibliothèque `responses` pour intercepter les appels HTTP
sans connexion réseau réelle.
"""

from __future__ import annotations

import json
from decimal import Decimal

import pytest
import responses as resp_lib

from api_client import SpringAPIClient
from exceptions import (
    AuthenticationError,
    BackendAPIError,
    DuplicateAlertError,
    NetworkError,
)

BASE_URL = "http://localhost:8080"


@pytest.fixture()
def client(monkeypatch):
    """Client avec base URL et API key de test."""
    monkeypatch.setenv("SPRING_BASE_URL", BASE_URL)
    monkeypatch.setenv("SPRING_API_KEY", "test_api_key")
    return SpringAPIClient()


@resp_lib.activate
class TestCreerAlerte:

    def test_cree_alerte_201(self, client):
        resp_lib.add(
            resp_lib.POST,
            f"{BASE_URL}/internal/alertes",
            json={"id": 1, "id_pret": "PRE-001", "statut": "ACTIVE"},
            status=201,
        )
        result = client.creer_alerte("PRE-001", 45, Decimal("250000.00"))
        assert result["id_pret"] == "PRE-001"
        assert result["statut"] == "ACTIVE"

    def test_cle_invalide_leve_authentication_error(self, client):
        resp_lib.add(
            resp_lib.POST,
            f"{BASE_URL}/internal/alertes",
            status=403,
        )
        with pytest.raises(AuthenticationError):
            client.creer_alerte("PRE-002", 30, Decimal("100000"))

    def test_doublon_leve_duplicate_alert_error(self, client):
        resp_lib.add(
            resp_lib.POST,
            f"{BASE_URL}/internal/alertes",
            json={"message": "Alerte ACTIVE déjà existante", "id_pret": "PRE-003"},
            status=409,
        )
        with pytest.raises(DuplicateAlertError) as exc_info:
            client.creer_alerte("PRE-003", 60, Decimal("500000"))
        assert exc_info.value.id_pret == "PRE-003"

    def test_erreur_serveur_leve_backend_api_error(self, client):
        resp_lib.add(
            resp_lib.POST,
            f"{BASE_URL}/internal/alertes",
            json={"error": "Internal Server Error"},
            status=500,
        )
        with pytest.raises(BackendAPIError) as exc_info:
            client.creer_alerte("PRE-004", 35, Decimal("200000"))
        assert exc_info.value.status_code == 500


@resp_lib.activate
class TestHealthCheck:

    def test_health_ok(self, client):
        resp_lib.add(
            resp_lib.GET,
            f"{BASE_URL}/api/health",
            json={"status": "UP"},
            status=200,
        )
        assert client.health_check() is True

    def test_health_down_returns_false(self, client):
        resp_lib.add(
            resp_lib.GET,
            f"{BASE_URL}/api/health",
            json={"status": "DOWN"},
            status=503,
        )
        assert client.health_check() is False


@resp_lib.activate
class TestRegisterFcmToken:

    def test_register_fcm_204(self, client):
        resp_lib.add(
            resp_lib.POST,
            f"{BASE_URL}/internal/fcm-token",
            status=204,
        )
        # Ne doit pas lever d'exception
        client.register_fcm_token(1, "firebase-token-xyz")

    def test_register_fcm_403(self, client):
        resp_lib.add(
            resp_lib.POST,
            f"{BASE_URL}/internal/fcm-token",
            status=403,
        )
        with pytest.raises(AuthenticationError):
            client.register_fcm_token(1, "invalid-token")


@resp_lib.activate
class TestNetworkError:

    def test_connexion_refusee_leve_network_error(self, client):
        resp_lib.add(
            resp_lib.POST,
            f"{BASE_URL}/internal/alertes",
            body=ConnectionError("Connection refused"),
        )
        with pytest.raises(NetworkError):
            client.creer_alerte("PRE-010", 45, Decimal("100000"))
