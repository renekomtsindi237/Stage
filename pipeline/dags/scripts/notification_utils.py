"""
notification_utils.py — Notifications FCM, SSE et e-mail depuis les DAGs.

Appelé par dag_collecte_epargne et dag_recouvrement après chaque traitement
pour informer les agents, responsables et directeurs des événements métier.
"""
from __future__ import annotations

import logging
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Any

import httpx

from pipeline.src.config import settings
from pipeline.src.database import readonly_session

logger = logging.getLogger(__name__)

# Variables d'environnement (lues depuis settings ou env)
import os

FCM_SERVER_KEY   = os.getenv("FCM_SERVER_KEY", "")
FCM_URL          = "https://fcm.googleapis.com/fcm/send"
SSE_ENDPOINT     = os.getenv("SSE_PUSH_URL", f"{settings.api.spring_base_url}/internal/sse/push")
SMTP_HOST        = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT        = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER        = os.getenv("SMTP_USER", "")
SMTP_PASSWORD    = os.getenv("SMTP_PASSWORD", "")
EMAIL_FROM       = os.getenv("EMAIL_FROM", "noreply@imf-pipeline.cm")

ROLES_DESTINATAIRES = {
    "AGENT":                  "agents",
    "RESPONSABLE_RECOUVREMENT": "responsables",
    "DIRECTEUR":              "directeurs",
    "ANALYSTE":               "analystes",
}


# ─── FCM (push mobile Flutter) ───────────────────────────────────────────────

def notifier_agents_fcm(type_notif: str, **ctx) -> int:
    """
    Envoie une notification FCM à tous les agents actifs.

    type_notif : clé de type (ex. 'COLLECTE_VALIDEE', 'OBJECTIF_ATTEINT').
    Retourne le nombre de tokens notifiés.
    """
    tokens = _recuperer_tokens_fcm(role="AGENT")
    if not tokens:
        logger.info("Aucun token FCM agent — skip notification %s", type_notif)
        return 0

    titre, corps = _construire_message_fcm(type_notif, role="AGENT")
    return _envoyer_fcm_multicast(tokens, titre, corps, data={"type": type_notif})


def notifier_responsables_sse(event: str, payload: dict | None = None, **ctx) -> int:
    """
    Pousse un événement SSE vers le backend Spring Boot (endpoint /internal/sse/push).
    Les responsables connectés au tableau de bord reçoivent l'événement en temps réel.

    Retourne 1 si succès, 0 sinon.
    """
    body = {"event": event, "role": "RESPONSABLE_RECOUVREMENT", "data": payload or {}}
    try:
        with httpx.Client(timeout=settings.api.connect_timeout) as client:
            resp = client.post(
                SSE_ENDPOINT,
                json=body,
                headers={"X-Api-Key": settings.api.api_key},
            )
            resp.raise_for_status()
        logger.info("SSE event '%s' envoyé aux responsables", event)
        return 1
    except Exception as exc:
        logger.warning("Échec SSE event '%s' : %s", event, exc)
        return 0


def notifier_directeurs_fcm(type_notif: str, **ctx) -> int:
    """
    Envoie une notification FCM aux directeurs d'IMF.
    Utilisé pour les alertes critiques (PAR COBAC dépassé, drift ML, etc.).
    """
    tokens = _recuperer_tokens_fcm(role="DIRECTEUR")
    if not tokens:
        logger.info("Aucun token FCM directeur — skip notification %s", type_notif)
        return 0

    titre, corps = _construire_message_fcm(type_notif, role="DIRECTEUR")
    return _envoyer_fcm_multicast(tokens, titre, corps, data={"type": type_notif})


# ─── E-mail ───────────────────────────────────────────────────────────────────

def envoyer_email_resume_quotidien(
    destinataires_role: str | list[str] = "DIRECTEUR",
    **ctx,
) -> int:
    """
    Envoie un résumé quotidien par e-mail aux utilisateurs du ou des rôles indiqués.

    Construit le corps HTML depuis les KPIs du jour en base et le transmet
    via SMTP (TLS). Retourne le nombre d'e-mails envoyés.
    """
    roles = [destinataires_role] if isinstance(destinataires_role, str) else destinataires_role
    destinataires: list[str] = []
    for role in roles:
        destinataires.extend(_recuperer_emails(role=role))
    # Déduplique les adresses
    destinataires = list(dict.fromkeys(destinataires))
    if not destinataires:
        logger.info("Aucun destinataire e-mail pour le rôle %s", destinataires_role)
        return 0

    kpis = _charger_kpis_journaliers()
    html  = _construire_html_resume(kpis)
    _date = kpis.get('date', "aujourd'hui")
    sujet = f"Résumé pipeline IMF — {_date}"

    n_ok = 0
    for email in destinataires:
        try:
            _envoyer_email_smtp(to=email, sujet=sujet, html=html)
            n_ok += 1
        except Exception as exc:
            logger.warning("Échec e-mail vers %s : %s", email, exc)

    logger.info("E-mails résumé envoyés : %d/%d", n_ok, len(destinataires))
    return n_ok


# ─── Helpers privés ───────────────────────────────────────────────────────────

def _recuperer_tokens_fcm(role: str) -> list[str]:
    """Retourne les tokens FCM des utilisateurs actifs du rôle donné."""
    sql = """
        SELECT fcm_token
        FROM app.users
        WHERE role = %(role)s
          AND actif = TRUE
          AND fcm_token IS NOT NULL
          AND fcm_token <> ''
    """
    try:
        with readonly_session() as cur:
            cur.execute(sql, {"role": role})
            return [row["fcm_token"] for row in cur.fetchall()]
    except Exception as exc:
        logger.warning("Impossible de récupérer les tokens FCM (%s) : %s", role, exc)
        return []


def _recuperer_emails(role: str) -> list[str]:
    sql = """
        SELECT email
        FROM app.users
        WHERE role = %(role)s
          AND actif = TRUE
          AND email IS NOT NULL
          AND email LIKE '%@%'
    """
    try:
        with readonly_session() as cur:
            cur.execute(sql, {"role": role})
            return [row["email"] for row in cur.fetchall()]
    except Exception as exc:
        logger.warning("Impossible de récupérer les e-mails (%s) : %s", role, exc)
        return []


def _envoyer_fcm_multicast(
    tokens: list[str],
    titre: str,
    corps: str,
    data: dict | None = None,
) -> int:
    """
    Envoie une notification FCM multicast.
    Découpe en chunks de 500 tokens (limite FCM).
    Retourne le nombre de tokens atteints.
    """
    if not FCM_SERVER_KEY:
        logger.warning("FCM_SERVER_KEY non configurée — notifications FCM désactivées")
        return 0

    headers = {
        "Authorization": f"key={FCM_SERVER_KEY}",
        "Content-Type": "application/json",
    }
    n_ok = 0
    chunk_size = 500
    for i in range(0, len(tokens), chunk_size):
        chunk = tokens[i : i + chunk_size]
        payload: dict[str, Any] = {
            "registration_ids": chunk,
            "notification": {"title": titre, "body": corps},
        }
        if data:
            payload["data"] = data

        try:
            with httpx.Client(timeout=10) as client:
                resp = client.post(FCM_URL, json=payload, headers=headers)
                resp.raise_for_status()
            result = resp.json()
            n_ok += result.get("success", len(chunk))
            if result.get("failure", 0) > 0:
                logger.warning(
                    "FCM : %d envois échoués sur %d", result["failure"], len(chunk)
                )
        except Exception as exc:
            logger.warning("Erreur FCM multicast (chunk %d) : %s", i // chunk_size, exc)

    logger.info("FCM multicast : %d tokens notifiés", n_ok)
    return n_ok


def _construire_message_fcm(type_notif: str, role: str) -> tuple[str, str]:
    """Retourne (titre, corps) selon le type d'événement et le rôle cible."""
    messages = {
        "COLLECTE_VALIDEE": (
            "Collecte validée",
            "Vos collectes de la journée ont été traitées et validées.",
        ),
        "OBJECTIF_ATTEINT": (
            "Objectif atteint !",
            "Félicitations — vous avez atteint votre objectif de collecte du cycle.",
        ),
        "OBJECTIF_NON_ATTEINT": (
            "Objectif en retard",
            "Votre taux de réalisation est inférieur à 70% à 3 jours de la fin du cycle.",
        ),
        "PAR_SEUIL_DEPASSE": (
            "Alerte PAR COBAC",
            "Le PAR90 dépasse le seuil réglementaire de 5%. Action requise.",
        ),
        "DRIFT_DETECTE": (
            "Drift ML détecté",
            "Le modèle MCRS présente un drift PSI ≥ 0.20. Retraining planifié.",
        ),
        "RISQUE_DEFAUT_IMMINENT": (
            "Risque défaut critique",
            "Des clients présentent un score MCRS critique. Consultez le tableau de bord.",
        ),
        "RESUME_JOURNALIER": (
            "Résumé journalier pipeline",
            "Le traitement quotidien est terminé. Consultez le rapport.",
        ),
    }
    default = ("Notification IMF Pipeline", f"Événement : {type_notif}")
    return messages.get(type_notif, default)


def _charger_kpis_journaliers() -> dict:
    """Charge les KPIs du jour depuis app.kpi_recouvrement_snapshots et raw.journal_ingestions."""
    kpis: dict[str, Any] = {}
    sql_kpi = """
        SELECT
            SUM(montant_encours)     AS encours_total,
            AVG(taux_par90)          AS par90_moyen,
            SUM(montant_recouvre_30j) AS recouvre_30j,
            COUNT(*)                  AS n_agences,
            MAX(date_snapshot)        AS date
        FROM app.kpi_recouvrement_snapshots
        WHERE date_snapshot = CURRENT_DATE
    """
    sql_journal = """
        SELECT dag_id, statut, lignes_valides, lignes_rejetees
        FROM raw.journal_ingestions
        WHERE DATE(created_at) = CURRENT_DATE
        ORDER BY created_at DESC
    """
    try:
        with readonly_session() as cur:
            cur.execute(sql_kpi)
            row = cur.fetchone()
            if row:
                kpis.update(row)

            cur.execute(sql_journal)
            kpis["journal_runs"] = cur.fetchall()
    except Exception as exc:
        logger.warning("Impossible de charger KPIs journaliers : %s", exc)

    return kpis


def _construire_html_resume(kpis: dict) -> str:
    date_str    = str(kpis.get("date", "N/A"))
    encours     = kpis.get("encours_total") or 0
    par90       = (kpis.get("par90_moyen") or 0) * 100
    recouvre    = kpis.get("recouvre_30j") or 0
    n_agences   = kpis.get("n_agences", 0)
    runs        = kpis.get("journal_runs") or []

    runs_html = "".join(
        f"<tr><td>{r.get('dag_id','')}</td>"
        f"<td>{r.get('statut','')}</td>"
        f"<td>{r.get('lignes_valides',0)}</td>"
        f"<td>{r.get('lignes_rejetees',0)}</td></tr>"
        for r in runs
    )

    return f"""
    <html><body style="font-family:sans-serif;max-width:700px;margin:auto">
    <h2>Résumé Pipeline IMF — {date_str}</h2>
    <h3>Indicateurs clés</h3>
    <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse">
      <tr><th>Indicateur</th><th>Valeur</th></tr>
      <tr><td>Encours total portefeuille</td><td>{encours:,.0f} FCFA</td></tr>
      <tr><td>PAR90 moyen</td><td>{par90:.2f}%</td></tr>
      <tr><td>Recouvré (30 derniers jours)</td><td>{recouvre:,.0f} FCFA</td></tr>
      <tr><td>Nombre d'agences</td><td>{n_agences}</td></tr>
    </table>
    <h3>Exécutions DAGs</h3>
    <table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse">
      <tr><th>DAG</th><th>Statut</th><th>Valides</th><th>Rejetés</th></tr>
      {runs_html}
    </table>
    <p style="color:#888;font-size:12px">Généré automatiquement par le pipeline IMF.</p>
    </body></html>
    """


def _envoyer_email_smtp(to: str, sujet: str, html: str) -> None:
    if not SMTP_USER or not SMTP_PASSWORD:
        logger.warning("SMTP non configuré — e-mail vers %s ignoré", to)
        return

    msg = MIMEMultipart("alternative")
    msg["Subject"] = sujet
    msg["From"]    = EMAIL_FROM
    msg["To"]      = to
    msg.attach(MIMEText(html, "html", "utf-8"))

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
        server.ehlo()
        server.starttls()
        server.login(SMTP_USER, SMTP_PASSWORD)
        server.sendmail(EMAIL_FROM, [to], msg.as_string())
