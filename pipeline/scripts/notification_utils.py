"""
notification_utils.py
Utilitaires pour les notifications email (SMTP) et push (FCM via API Spring Boot).
"""
import logging
import os
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from typing import Optional

import requests

logger = logging.getLogger(__name__)


def envoyer_email(
    destinataire: str,
    sujet: str,
    corps_html: str,
    destinataires_cc: Optional[list[str]] = None,
) -> bool:
    """
    Envoie un email via SMTP.
    Variables d'environnement requises : SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD.
    """
    smtp_host = os.environ.get("SMTP_HOST", "smtp.gmail.com")
    smtp_port = int(os.environ.get("SMTP_PORT", 587))
    smtp_user = os.environ["SMTP_USER"]
    smtp_password = os.environ["SMTP_PASSWORD"]
    expediteur = os.environ.get("SMTP_FROM", smtp_user)

    msg = MIMEMultipart("alternative")
    msg["Subject"] = sujet
    msg["From"] = expediteur
    msg["To"] = destinataire
    if destinataires_cc:
        msg["Cc"] = ", ".join(destinataires_cc)

    msg.attach(MIMEText(corps_html, "html", "utf-8"))

    try:
        with smtplib.SMTP(smtp_host, smtp_port) as server:
            server.starttls()
            server.login(smtp_user, smtp_password)
            tous = [destinataire] + (destinataires_cc or [])
            server.sendmail(expediteur, tous, msg.as_string())
        logger.info("Email envoyé à %s — Sujet : %s", destinataire, sujet)
        return True
    except Exception as exc:
        logger.error("Échec envoi email à %s : %s", destinataire, exc)
        return False


def notifier_dsi_erreur_ingestion(
    source: str,
    nom_fichier: str,
    message_erreur: str,
    dag_run_id: str,
) -> None:
    """Alerte le DSI en cas d'échec d'ingestion."""
    destinataire = os.environ.get("DSI_EMAIL", "dsi@imf.cm")
    sujet = f"[IMF Pipeline] ⚠️ Erreur ingestion {source} — {nom_fichier}"
    corps = f"""
    <h3>Erreur lors de l'ingestion {source}</h3>
    <table border="1" cellpadding="5">
        <tr><td><b>Fichier</b></td><td>{nom_fichier}</td></tr>
        <tr><td><b>Source</b></td><td>{source}</td></tr>
        <tr><td><b>DAG Run ID</b></td><td>{dag_run_id}</td></tr>
        <tr><td><b>Erreur</b></td><td style="color:red">{message_erreur}</td></tr>
    </table>
    <p>Vérifiez les logs Airflow pour plus de détails.</p>
    """
    envoyer_email(destinataire, sujet, corps)


def notifier_alertes_impayes(
    destinataire_rr: str,
    alertes: list[dict],
    dag_run_id: str,
) -> None:
    """
    Envoie un résumé des nouvelles alertes impayés au responsable recouvrement.
    alertes : liste de dicts {id_pret, nom_client, montant_impaye, jours_retard, niveau_par}
    """
    if not alertes:
        return

    lignes_html = "".join(
        f"""<tr>
            <td>{a.get('id_pret','')}</td>
            <td>{a.get('nom_client','')}</td>
            <td style="text-align:right">{a.get('montant_impaye', 0):,.0f} XAF</td>
            <td>{a.get('jours_retard', 0)} jours</td>
            <td style="color:red;font-weight:bold">{a.get('niveau_par','')}</td>
        </tr>"""
        for a in alertes
    )

    sujet = f"[IMF Pipeline] 🔔 {len(alertes)} nouvelles alertes impayés — {dag_run_id[:10]}"
    corps = f"""
    <h3>{len(alertes)} nouvelles alertes impayés détectées</h3>
    <table border="1" cellpadding="5" style="border-collapse:collapse">
        <thead>
            <tr style="background:#f57f17;color:white">
                <th>ID Prêt</th><th>Client</th>
                <th>Montant impayé</th><th>Retard</th><th>Niveau PAR</th>
            </tr>
        </thead>
        <tbody>{lignes_html}</tbody>
    </table>
    <p>Connectez-vous au dashboard pour gérer ces alertes.</p>
    """
    envoyer_email(destinataire_rr, sujet, corps)


def envoyer_push_fcm_via_api(
    id_alerte: int,
    titre: str,
    corps: str,
    id_agence: str,
) -> bool:
    """
    Déclenche l'envoi de push FCM via l'API Spring Boot interne.
    L'API Spring Boot gère les tokens FCM des agents par agence.
    """
    api_url = os.environ.get("SPRING_BOOT_INTERNAL_URL", "http://springboot-api:8080")
    endpoint = f"{api_url}/internal/alertes/notify"
    api_key = os.environ.get("INTERNAL_API_KEY", "")

    payload = {
        "id_alerte": id_alerte,
        "titre": titre,
        "corps": corps,
        "id_agence": id_agence,
    }

    try:
        resp = requests.post(
            endpoint,
            json=payload,
            headers={"X-Internal-Key": api_key},
            timeout=10,
        )
        resp.raise_for_status()
        logger.info("Push FCM déclenché pour alerte %d, agence %s", id_alerte, id_agence)
        return True
    except requests.exceptions.RequestException as exc:
        logger.error("Échec push FCM alerte %d : %s", id_alerte, exc)
        return False
