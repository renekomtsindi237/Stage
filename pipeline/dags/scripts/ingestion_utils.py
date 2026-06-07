"""
ingestion_utils.py — Journalisation pipeline et alertes opérationnelles.

Fonctions partagées par tous les DAGs pour :
- Écrire dans raw.journal_ingestions le bilan d'exécution.
- Générer des alertes dans app.alertes_operationnelles.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone

from pipeline.src.database import db_session

logger = logging.getLogger(__name__)

TYPES_ALERTE_VALIDES = {
    "OBJECTIF_NON_ATTEINT",
    "TAUX_REJET_ELEVE",
    "AGENT_INACTIF",
    "SYNCHRONISATION_RETARD",
    "PAR_SEUIL_DEPASSE",
    "PROVISION_INSUFFISANTE",
    "DOSSIER_SANS_ACTION",
    "PROMESSE_ECHEANCE",
    "DRIFT_DETECTE",
}


def log_journal(
    dag_id: str,
    table_cible: str,
    lignes_lues: int = 0,
    lignes_valides: int = 0,
    lignes_rejetees: int = 0,
    statut: str = "SUCCESS",
    message: str = "",
    **ctx,
) -> None:
    """
    Écrit le bilan d'exécution d'un DAG dans raw.journal_ingestions.
    Appelé systématiquement en fin de DAG (trigger_rule=ALL_DONE).
    """
    ti = ctx.get("ti")
    run_id = ti.run_id if ti else "manual"
    duree_ms = 0
    if ti:
        try:
            start = ti.start_date
            end = ti.end_date or datetime.now(timezone.utc)
            duree_ms = int((end - start).total_seconds() * 1000)
        except Exception:
            pass

    sql = """
        INSERT INTO raw.journal_ingestions (
            dag_id, run_id, table_cible, statut,
            lignes_lues, lignes_valides, lignes_rejetees,
            duree_ms, message, created_at
        ) VALUES (
            %(dag_id)s, %(run_id)s, %(table_cible)s, %(statut)s,
            %(lignes_lues)s, %(lignes_valides)s, %(lignes_rejetees)s,
            %(duree_ms)s, %(message)s, NOW()
        )
    """
    try:
        with db_session() as cur:
            cur.execute(
                sql,
                {
                    "dag_id": dag_id,
                    "run_id": run_id,
                    "table_cible": table_cible,
                    "statut": statut,
                    "lignes_lues": lignes_lues,
                    "lignes_valides": lignes_valides,
                    "lignes_rejetees": lignes_rejetees,
                    "duree_ms": duree_ms,
                    "message": message[:500],
                },
            )
        logger.info(
            "Journal [%s] écrit : %s — %d lignes valides",
            dag_id,
            statut,
            lignes_valides,
        )
    except Exception as exc:
        logger.error("Échec écriture journal pour DAG %s : %s", dag_id, exc)


def generer_alertes_operationnelles(
    types: list[str],
    seuil_taux_rejet_pct: float = 10.0,
    seuil_inactivite_heures: int = 48,
    seuil_par90_pct: float = 5.0,
    **ctx,
) -> int:
    """
    Génère des alertes opérationnelles dans app.alertes_operationnelles.

    Chaque type d'alerte est évalué selon ses propres règles SQL.
    Retourne le nombre d'alertes générées.
    """
    n_alertes = 0

    if "PAR_SEUIL_DEPASSE" in types:
        n_alertes += _alerte_par_depasse(seuil_par90_pct)

    if "OBJECTIF_NON_ATTEINT" in types:
        n_alertes += _alerte_objectif_non_atteint()

    if "AGENT_INACTIF" in types:
        n_alertes += _alerte_agent_inactif(seuil_inactivite_heures)

    if "TAUX_REJET_ELEVE" in types:
        n_alertes += _alerte_taux_rejet(seuil_taux_rejet_pct)

    if "DOSSIER_SANS_ACTION" in types:
        n_alertes += _alerte_dossier_sans_action()

    logger.info("Alertes opérationnelles générées : %d", n_alertes)
    return n_alertes


def _inserer_alerte(
    imf_id: int,
    agence_id: int | None,
    type_alerte: str,
    message: str,
    niveau: str = "AVERTISSEMENT",
    entite_id: int | None = None,
    entite_type: str | None = None,
) -> None:
    sql = """
        INSERT INTO app.alertes_operationnelles
            (imf_id, agence_id, type_alerte, message, niveau, entite_id, entite_type, created_at)
        VALUES
            (%(imf_id)s, %(agence_id)s, %(type_alerte)s, %(message)s,
             %(niveau)s, %(entite_id)s, %(entite_type)s, NOW())
        ON CONFLICT DO NOTHING
    """
    with db_session() as cur:
        cur.execute(
            sql,
            {
                "imf_id": imf_id,
                "agence_id": agence_id,
                "type_alerte": type_alerte,
                "message": message[:500],
                "niveau": niveau,
                "entite_id": entite_id,
                "entite_type": entite_type,
            },
        )


def _alerte_par_depasse(seuil_pct: float) -> int:
    sql = """
        SELECT imf_id, agence_id, taux_par90 * 100 AS taux_par90_pct
        FROM app.kpi_recouvrement_snapshots
        WHERE date_snapshot = CURRENT_DATE
          AND taux_par90 * 100 > %(seuil)s
    """
    n = 0
    with db_session() as cur:
        cur.execute(sql, {"seuil": seuil_pct})
        for row in cur.fetchall():
            _inserer_alerte(
                imf_id=row["imf_id"],
                agence_id=row["agence_id"],
                type_alerte="PAR_SEUIL_DEPASSE",
                message=f"PAR90 = {row['taux_par90_pct']:.1f}% — seuil COBAC {seuil_pct}% dépassé",
                niveau="CRITIQUE",
                entite_type="AGENCE",
                entite_id=row["agence_id"],
            )
            n += 1
    return n


def _alerte_objectif_non_atteint() -> int:
    sql = """
        SELECT
            oc.imf_id, oc.agence_id, oc.agent_id,
            oc.taux_realisation_montant,
            cc.date_fin,
            (cc.date_fin - CURRENT_DATE) AS jours_restants
        FROM app.objectifs_collecte oc
        JOIN app.cycles_collecte cc ON cc.id = oc.cycle_id
        WHERE cc.statut = 'EN_COURS'
          AND (cc.date_fin - CURRENT_DATE) <= 3
          AND oc.taux_realisation_montant < 70
    """
    n = 0
    with db_session() as cur:
        cur.execute(sql)
        for row in cur.fetchall():
            _inserer_alerte(
                imf_id=row["imf_id"],
                agence_id=row["agence_id"],
                type_alerte="OBJECTIF_NON_ATTEINT",
                message=(
                    f"Agent {row['agent_id']} — taux réalisation {row['taux_realisation_montant']:.0f}% "
                    f"(J-{row['jours_restants']} fin de cycle)"
                ),
                niveau="AVERTISSEMENT",
                entite_type="AGENT",
                entite_id=row["agent_id"],
            )
            n += 1
    return n


def _alerte_agent_inactif(seuil_heures: int) -> int:
    sql = """
        SELECT u.imf_id, u.agence_id, u.id AS agent_id, MAX(ce.created_at) AS derniere_collecte
        FROM app.users u
        LEFT JOIN app.collectes_epargne ce ON ce.agent_id = u.id AND ce.imf_id = u.imf_id
        WHERE u.role = 'AGENT'
          AND u.actif = TRUE
        GROUP BY u.imf_id, u.agence_id, u.id
        HAVING MAX(ce.created_at) < NOW() - %(interval)s::INTERVAL
            OR MAX(ce.created_at) IS NULL
    """
    n = 0
    interval = f"{seuil_heures} hours"
    with db_session() as cur:
        cur.execute(sql, {"interval": interval})
        for row in cur.fetchall():
            _inserer_alerte(
                imf_id=row["imf_id"],
                agence_id=row["agence_id"],
                type_alerte="AGENT_INACTIF",
                message=f"Agent {row['agent_id']} — aucune collecte depuis {seuil_heures}h",
                niveau="INFORMATION",
                entite_type="AGENT",
                entite_id=row["agent_id"],
            )
            n += 1
    return n


def _alerte_taux_rejet(seuil_pct: float) -> int:
    sql = """
        SELECT imf_id, agence_id,
               COUNT(*) FILTER (WHERE statut = 'REJETEE') * 100.0 / COUNT(*) AS taux_rejet
        FROM app.collectes_epargne
        WHERE created_at >= NOW() - INTERVAL '24 hours'
        GROUP BY imf_id, agence_id
        HAVING COUNT(*) FILTER (WHERE statut = 'REJETEE') * 100.0 / COUNT(*) > %(seuil)s
    """
    n = 0
    with db_session() as cur:
        cur.execute(sql, {"seuil": seuil_pct})
        for row in cur.fetchall():
            _inserer_alerte(
                imf_id=row["imf_id"],
                agence_id=row["agence_id"],
                type_alerte="TAUX_REJET_ELEVE",
                message=f"Taux de rejet collectes = {row['taux_rejet']:.1f}% (seuil: {seuil_pct}%)",
                niveau="AVERTISSEMENT",
                entite_type="AGENCE",
                entite_id=row["agence_id"],
            )
            n += 1
    return n


def _alerte_dossier_sans_action() -> int:
    sql = """
        SELECT dr.imf_id, dr.agence_id, dr.id AS dossier_id, dr.client_id,
               (CURRENT_DATE - MAX(ar.date_action)) AS jours_sans_action
        FROM app.dossiers_recouvrement dr
        LEFT JOIN app.actions_recouvrement ar ON ar.dossier_id = dr.id
        WHERE dr.statut = 'OUVERT'
        GROUP BY dr.imf_id, dr.agence_id, dr.id, dr.client_id
        HAVING MAX(ar.date_action) < CURRENT_DATE - INTERVAL '14 days'
            OR MAX(ar.date_action) IS NULL
    """
    n = 0
    with db_session() as cur:
        cur.execute(sql)
        for row in cur.fetchall():
            _inserer_alerte(
                imf_id=row["imf_id"],
                agence_id=row["agence_id"],
                type_alerte="DOSSIER_SANS_ACTION",
                message=f"Dossier {row['dossier_id']} — {row['jours_sans_action']} jours sans action de recouvrement",
                niveau="AVERTISSEMENT",
                entite_type="DOSSIER",
                entite_id=row["dossier_id"],
            )
            n += 1
    return n
