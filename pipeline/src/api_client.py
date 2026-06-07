"""
api_client.py — Client HTTP pour l'API interne Spring Boot.

Expose des méthodes typées pour chaque endpoint /internal/*.
Les erreurs HTTP sont converties en exceptions du pipeline.
"""

from __future__ import annotations

import logging
from decimal import Decimal
from typing import Any

import requests
from requests.exceptions import ConnectionError, Timeout, RequestException
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
    before_sleep_log,
)

from config import settings
from exceptions import (
    AuthenticationError,
    BackendAPIError,
    DuplicateAlertError,
    NetworkError,
)

logger = logging.getLogger(__name__)


class SpringAPIClient:
    """
    Client pour l'API interne du backend Spring Boot.

    Toutes les requêtes portent le header X-Internal-Api-Key.
    Les erreurs réseau sont retryées automatiquement (tenacity).
    """

    def __init__(self) -> None:
        self._base_url = settings.api.spring_base_url.rstrip("/")
        self._api_key = settings.api.api_key
        self._session = requests.Session()
        self._session.headers.update({
            "X-Internal-Api-Key": self._api_key,
            "Content-Type": "application/json",
            "Accept": "application/json",
        })
        self._timeout = (
            settings.api.connect_timeout,
            settings.api.read_timeout,
        )

    # ── Alertes ───────────────────────────────────────────────────────────────

    def creer_alerte(
        self,
        id_pret: str,
        jours_retard: int,
        montant_en_retard: Decimal,
    ) -> dict[str, Any]:
        """
        POST /internal/alertes — crée une alerte impayé.

        Returns:
            Dictionnaire {id, id_pret, statut} retourné par le backend.

        Raises:
            DuplicateAlertError: alerte ACTIVE déjà existante (409).
            AuthenticationError: clé API invalide (403).
            BackendAPIError: autre erreur HTTP.
            NetworkError: problème réseau.
        """
        url = f"{self._base_url}/internal/alertes"
        payload = {
            "id_pret": id_pret,
            "jours_retard": jours_retard,
            "montant_en_retard": str(montant_en_retard),
        }
        response = self._post(url, payload)
        return response.json()

    # ── FCM Token ─────────────────────────────────────────────────────────────

    def register_fcm_token(self, user_id: int, fcm_token: str) -> None:
        """
        POST /internal/fcm-token — enregistre un token Firebase.

        Raises:
            AuthenticationError: clé API invalide (403).
            BackendAPIError: autre erreur HTTP.
            NetworkError: problème réseau.
        """
        url = f"{self._base_url}/internal/fcm-token"
        payload = {"user_id": user_id, "fcm_token": fcm_token}
        self._post(url, payload)

    # ── Méthodes internes ─────────────────────────────────────────────────────

    @retry(
        retry=retry_if_exception_type(NetworkError),
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=2, max=10),
        before_sleep=before_sleep_log(logger, logging.WARNING),
        reraise=True,
    )
    def _post(self, url: str, payload: dict[str, Any]) -> requests.Response:
        """
        Effectue un POST HTTP avec retry automatique sur les erreurs réseau.

        Raises:
            NetworkError: timeout ou connexion refusée.
            AuthenticationError: 403.
            DuplicateAlertError: 409 sur /internal/alertes.
            BackendAPIError: autre 4xx/5xx.
        """
        try:
            response = self._session.post(url, json=payload, timeout=self._timeout)
        except (ConnectionError, Timeout) as exc:
            raise NetworkError(url, cause=exc) from exc
        except RequestException as exc:
            raise NetworkError(url, cause=exc) from exc

        return self._handle_response(url, response)

    def _handle_response(
        self, url: str, response: requests.Response
    ) -> requests.Response:
        """Lève l'exception appropriée selon le code HTTP."""
        if response.status_code in (200, 201, 204):
            return response

        if response.status_code == 403:
            raise AuthenticationError(url)

        if response.status_code == 409:
            # Doublon d'alerte — extrait id_pret du payload si possible
            try:
                body = response.json()
                id_pret = str(body.get("id_pret", "inconnu"))
            except Exception:
                id_pret = "inconnu"
            raise DuplicateAlertError(id_pret)

        raise BackendAPIError(url, response.status_code, response.text)

    def health_check(self) -> bool:
        """
        GET /api/health — vérifie que le backend est accessible.

        Returns:
            True si le backend répond 200.

        Raises:
            NetworkError: connexion impossible.
        """
        url = f"{self._base_url}/api/health"
        try:
            resp = self._session.get(url, timeout=(5, 10))
            return resp.status_code == 200
        except (ConnectionError, Timeout) as exc:
            raise NetworkError(url, cause=exc) from exc
        except RequestException as exc:
            raise NetworkError(url, cause=exc) from exc

    def close(self) -> None:
        """Libère la session HTTP."""
        self._session.close()

    def __enter__(self) -> "SpringAPIClient":
        return self

    def __exit__(self, *args: Any) -> None:
        self.close()
