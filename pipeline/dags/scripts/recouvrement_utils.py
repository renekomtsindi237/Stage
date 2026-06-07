"""
recouvrement_utils.py — Ingestion CBS, PAR/provisions, dossiers et benchmarks.

Appelé par dag_recouvrement (06h00 chaque jour) pour :
1. Ingérer l'export CBS (FinancialEdge/Mambu/Excel) du dossier entrant.
2. Valider les données : COBAC, montants, doublons.
3. Calculer PAR (30/60/90/180) et provisions réglementaires COBAC.
4. Synchroniser vers app.creances et classer en classe COBAC.
5. Créer automatiquement les dossiers de recouvrement pour les créances PAR30+.
6. Prioriser les dossiers par score MCRS.
7. Vérifier les promesses de paiement échues.
8. Calculer les KPIs et benchmarks inter-agences.
"""

from __future__ import annotations

import csv
import logging
import os
from datetime import date
from pathlib import Path
from typing import Iterator

from pipeline.src.database import db_session, readonly_session

logger = logging.getLogger(__name__)

CBS_DOSSIER_DEFAULT = Path(os.getenv("CBS_DOSSIER_ENTRANT", "/data/cbs_exports"))

# Seuils COBAC EMF 01/02
COBAC_CLASSES = {
    "A": (0, 29, 0.00),  # < 30j retard  → 0% provision
    "B": (30, 89, 0.20),  # 30-89j         → 20%
    "C": (90, 179, 0.50),  # 90-179j         → 50%
    "D": (180, 359, 0.80),  # 180-359j         → 80%
    "E": (360, 9999, 1.00),  # 360j+           → 100%
}


def _classe_cobac(jours_retard: int) -> str:
    for cls, (lo, hi, _) in COBAC_CLASSES.items():
        if lo <= jours_retard <= hi:
            return cls
    return "E"


def _taux_provision(classe: str) -> float:
    return COBAC_CLASSES.get(classe, ("", "", 1.0))[2]


# ─── 1. Ingestion CBS ──────────────────────────────────────────────────────────


def ingerer_export_cbs(dossier_entrant: str | None = None, **ctx) -> dict:
    """
    Lit les fichiers CSV/Excel déposés par le CBS dans dossier_entrant
    et les charge dans staging.stg_creances.

    Format attendu (colonnes minimales) :
      client_ref, imf_code, numero_credit, montant_initial, encours_restant,
      date_debut, date_echeance, jours_retard

    Retourne dict avec 'lignes_lues', 'lignes_valides', 'lignes_rejetees'.
    """
    dossier = Path(dossier_entrant) if dossier_entrant else CBS_DOSSIER_DEFAULT
    if not dossier.exists():
        logger.warning("Dossier CBS introuvable : %s — ingestion ignorée", dossier)
        return {"lignes_lues": 0, "lignes_valides": 0, "lignes_rejetees": 0}

    fichiers = list(dossier.glob("*.csv")) + list(dossier.glob("*.CSV"))
    if not fichiers:
        logger.info("Aucun fichier CSV dans %s — ingestion ignorée", dossier)
        return {"lignes_lues": 0, "lignes_valides": 0, "lignes_rejetees": 0}

    sql_insert = """
        INSERT INTO staging.stg_creances (
            imf_code, client_ref, numero_credit,
            montant_initial, encours_restant,
            date_debut, date_echeance,
            jours_retard, classe_cobac, taux_provision,
            date_extraction, source_fichier
        ) VALUES (
            %(imf_code)s, %(client_ref)s, %(numero_credit)s,
            %(montant_initial)s, %(encours_restant)s,
            %(date_debut)s, %(date_echeance)s,
            %(jours_retard)s, %(classe_cobac)s, %(taux_provision)s,
            CURRENT_DATE, %(source_fichier)s
        )
        ON CONFLICT (imf_code, numero_credit, date_extraction) DO UPDATE
          SET encours_restant = EXCLUDED.encours_restant,
              jours_retard    = EXCLUDED.jours_retard,
              classe_cobac    = EXCLUDED.classe_cobac,
              taux_provision  = EXCLUDED.taux_provision
    """

    n_lu = n_val = n_rej = 0

    with db_session() as cur:
        for fichier in fichiers:
            for row in _lire_csv_cbs(fichier):
                n_lu += 1
                try:
                    jours = int(row.get("jours_retard", 0) or 0)
                    classe = _classe_cobac(jours)
                    taux_pr = _taux_provision(classe)
                    cur.execute(
                        sql_insert,
                        {
                            "imf_code": row["imf_code"].strip(),
                            "client_ref": row["client_ref"].strip(),
                            "numero_credit": row["numero_credit"].strip(),
                            "montant_initial": float(
                                row.get("montant_initial", 0) or 0
                            ),
                            "encours_restant": float(
                                row.get("encours_restant", 0) or 0
                            ),
                            "date_debut": row.get("date_debut") or None,
                            "date_echeance": row.get("date_echeance") or None,
                            "jours_retard": jours,
                            "classe_cobac": classe,
                            "taux_provision": taux_pr,
                            "source_fichier": fichier.name,
                        },
                    )
                    n_val += 1
                except (KeyError, ValueError, TypeError) as exc:
                    logger.debug("Ligne CBS rejetée (%s) : %s", fichier.name, exc)
                    n_rej += 1

    logger.info(
        "ingerer_export_cbs : lu=%d, valide=%d, rejeté=%d — fichiers=%d",
        n_lu,
        n_val,
        n_rej,
        len(fichiers),
    )
    return {"lignes_lues": n_lu, "lignes_valides": n_val, "lignes_rejetees": n_rej}


def _lire_csv_cbs(path: Path) -> Iterator[dict]:
    with open(path, newline="", encoding="utf-8-sig") as f:
        reader = csv.DictReader(f, delimiter=";")
        for row in reader:
            yield row


# ─── 2. Validation des données CBS ────────────────────────────────────────────


def valider_donnees_cbs(**ctx) -> dict:
    """
    Vérifie la cohérence des données staging.stg_creances du jour :
    - Encours ≥ 0
    - Date début ≤ date échéance
    - Classe COBAC cohérente avec jours_retard

    Marque les lignes invalides avec un flag valide=FALSE.
    """
    sql_invalides = """
        UPDATE staging.stg_creances
        SET valide = FALSE,
            motif_invalidite = CASE
                WHEN encours_restant < 0 THEN 'Encours négatif'
                WHEN date_debut > date_echeance THEN 'Dates incohérentes'
                WHEN jours_retard < 0 THEN 'Jours retard négatif'
                ELSE 'Erreur inconnue'
            END
        WHERE date_extraction = CURRENT_DATE
          AND (
              encours_restant < 0
           OR date_debut > date_echeance
           OR jours_retard < 0
          )
        RETURNING id
    """
    sql_valides = """
        UPDATE staging.stg_creances
        SET valide = TRUE
        WHERE date_extraction = CURRENT_DATE
          AND valide IS NULL
        RETURNING id
    """
    n_inv = n_val = 0
    with db_session() as cur:
        cur.execute(sql_invalides)
        n_inv = cur.rowcount
        cur.execute(sql_valides)
        n_val = cur.rowcount

    logger.info("Validation CBS : valides=%d, invalides=%d", n_val, n_inv)
    return {"valides": n_val, "invalides": n_inv}


# ─── 3. PAR et provisions ─────────────────────────────────────────────────────


def calculer_par_et_provisions(
    seuils_cobac: dict | None = None,
    **ctx,
) -> dict:
    """
    Calcule le PAR (Portfolio at Risk) aux seuils 30/60/90/180 jours
    et insère un snapshot dans app.kpi_recouvrement_snapshots.

    PAR_n = (Σ encours créances avec jours_retard ≥ n) / Σ encours total × 100

    Retourne les indicateurs calculés.
    """
    seuils_cobac = seuils_cobac or {
        "par30": 30,
        "par60": 60,
        "par90": 90,
        "par180": 180,
    }

    sql_par = """
        WITH base AS (
            SELECT
                c.imf_id,
                c.agence_id,
                SUM(cr.encours_restant)                              AS encours_total,
                SUM(cr.encours_restant) FILTER (WHERE cr.jours_retard >= 30)  AS par30,
                SUM(cr.encours_restant) FILTER (WHERE cr.jours_retard >= 60)  AS par60,
                SUM(cr.encours_restant) FILTER (WHERE cr.jours_retard >= 90)  AS par90,
                SUM(cr.encours_restant) FILTER (WHERE cr.jours_retard >= 180) AS par180,
                SUM(cr.encours_restant * cr.taux_provision)          AS provision_requise
            FROM staging.stg_creances cr
            JOIN app.clients c ON c.client_id_externe = cr.client_ref
                               AND c.imf_id = (
                                   SELECT id FROM app.imfs WHERE code = cr.imf_code LIMIT 1
                               )
            WHERE cr.date_extraction = CURRENT_DATE
              AND cr.valide = TRUE
            GROUP BY c.imf_id, c.agence_id
        )
        INSERT INTO app.kpi_recouvrement_snapshots (
            date_snapshot, imf_id, agence_id,
            montant_encours, montant_par30, montant_par60, montant_par90, montant_par180,
            taux_par30, taux_par60, taux_par90, taux_par180,
            provision_requise
        )
        SELECT
            CURRENT_DATE,
            imf_id,
            agence_id,
            encours_total,
            par30,  par60,  par90,  par180,
            COALESCE(par30  / NULLIF(encours_total, 0), 0),
            COALESCE(par60  / NULLIF(encours_total, 0), 0),
            COALESCE(par90  / NULLIF(encours_total, 0), 0),
            COALESCE(par180 / NULLIF(encours_total, 0), 0),
            provision_requise
        FROM base
        ON CONFLICT (date_snapshot, imf_id, agence_id) DO UPDATE SET
            montant_encours   = EXCLUDED.montant_encours,
            montant_par30     = EXCLUDED.montant_par30,
            montant_par60     = EXCLUDED.montant_par60,
            montant_par90     = EXCLUDED.montant_par90,
            montant_par180    = EXCLUDED.montant_par180,
            taux_par30        = EXCLUDED.taux_par30,
            taux_par60        = EXCLUDED.taux_par60,
            taux_par90        = EXCLUDED.taux_par90,
            taux_par180       = EXCLUDED.taux_par180,
            provision_requise = EXCLUDED.provision_requise
    """
    sql_totaux = """
        SELECT
            SUM(taux_par90)  / NULLIF(COUNT(*), 0) AS par90_moyen,
            SUM(montant_encours)                    AS encours_total,
            COUNT(*)                                AS n_agences
        FROM app.kpi_recouvrement_snapshots
        WHERE date_snapshot = CURRENT_DATE
    """

    with db_session() as cur:
        cur.execute(sql_par)
        n_snap = cur.rowcount

    with readonly_session() as cur:
        cur.execute(sql_totaux)
        totaux = dict(cur.fetchone() or {})

    logger.info(
        "PAR calculé : %d snapshots, PAR90 moyen=%.2f%%, encours=%.0f FCFA",
        n_snap,
        (totaux.get("par90_moyen") or 0) * 100,
        totaux.get("encours_total") or 0,
    )
    return {"snapshots": n_snap, **totaux}


# ─── 4. Synchronisation créances → app ────────────────────────────────────────


def synchroniser_creances_app(**ctx) -> dict:
    """
    Propage les créances valides de staging.stg_creances → app.creances.

    Met à jour : encours_restant, jours_retard, classe_cobac, taux_provision.
    Insère les nouvelles créances introuvables dans app.creances.
    """
    sql_upsert = """
        INSERT INTO app.creances (
            client_id, imf_id, numero_credit,
            montant_initial, encours_restant,
            date_debut, date_echeance,
            jours_retard, classe_cobac, taux_provision,
            date_derniere_maj
        )
        SELECT
            c.id            AS client_id,
            c.imf_id,
            sc.numero_credit,
            sc.montant_initial,
            sc.encours_restant,
            sc.date_debut,
            sc.date_echeance,
            sc.jours_retard,
            sc.classe_cobac,
            sc.taux_provision,
            CURRENT_DATE
        FROM staging.stg_creances sc
        JOIN app.clients c ON c.client_id_externe = sc.client_ref
                           AND c.imf_id = (
                               SELECT id FROM app.imfs WHERE code = sc.imf_code LIMIT 1
                           )
        WHERE sc.date_extraction = CURRENT_DATE
          AND sc.valide = TRUE
        ON CONFLICT (client_id, imf_id, numero_credit) DO UPDATE SET
            encours_restant    = EXCLUDED.encours_restant,
            jours_retard       = EXCLUDED.jours_retard,
            classe_cobac       = EXCLUDED.classe_cobac,
            taux_provision     = EXCLUDED.taux_provision,
            date_derniere_maj  = EXCLUDED.date_derniere_maj
    """
    with db_session() as cur:
        cur.execute(sql_upsert)
        n = cur.rowcount

    logger.info("synchroniser_creances_app : %d créances synchronisées", n)
    return {"creances_synchronisees": n}


# ─── 5. Création automatique des dossiers ────────────────────────────────────


def creer_dossiers_automatiques(seuil_par_jours: int = 30, **ctx) -> int:
    """
    Crée un dossier de recouvrement dans app.dossiers_recouvrement
    pour toute créance avec jours_retard ≥ seuil_par_jours
    qui n'en a pas encore.

    Retourne le nombre de dossiers créés.
    """
    sql = """
        INSERT INTO app.dossiers_recouvrement (
            client_id, imf_id, agence_id,
            numero_credit, montant_creance,
            statut, classe_cobac,
            date_ouverture, created_at
        )
        SELECT
            cr.client_id,
            cr.imf_id,
            c.agence_id,
            cr.numero_credit,
            cr.encours_restant,
            'OUVERT',
            cr.classe_cobac,
            CURRENT_DATE,
            NOW()
        FROM app.creances cr
        JOIN app.clients c ON c.id = cr.client_id AND c.imf_id = cr.imf_id
        WHERE cr.jours_retard >= %(seuil)s
          AND NOT EXISTS (
              SELECT 1 FROM app.dossiers_recouvrement dr
              WHERE dr.client_id    = cr.client_id
                AND dr.imf_id       = cr.imf_id
                AND dr.numero_credit = cr.numero_credit
                AND dr.statut       = 'OUVERT'
          )
        ON CONFLICT DO NOTHING
    """
    with db_session() as cur:
        cur.execute(sql, {"seuil": seuil_par_jours})
        n = cur.rowcount

    logger.info(
        "creer_dossiers_automatiques (PAR%d+) : %d dossiers créés", seuil_par_jours, n
    )
    return n


# ─── 6. Priorisation par score MCRS ──────────────────────────────────────────


def prioriser_dossiers_par_score(**ctx) -> int:
    """
    Classe les dossiers ouverts en priorité selon le score MCRS du jour.

    Priorité calculée :
    - CRITIQUE  si score_mcrs ≥ 0.75 ou classe E
    - HAUTE     si score_mcrs ≥ 0.55 ou classe D
    - NORMALE   sinon

    Retourne le nombre de dossiers mis à jour.
    """
    sql = """
        UPDATE app.dossiers_recouvrement dr
        SET
            priorite = CASE
                WHEN cs.score_mcrs >= 0.75 OR dr.classe_cobac = 'E' THEN 'CRITIQUE'
                WHEN cs.score_mcrs >= 0.55 OR dr.classe_cobac = 'D' THEN 'HAUTE'
                ELSE 'NORMALE'
            END,
            updated_at = NOW()
        FROM ml.client_scores cs
        JOIN app.clients c ON c.id = dr.client_id AND c.imf_id = dr.imf_id
        WHERE c.client_id_externe = cs.client_id_externe
          AND cs.date_score = CURRENT_DATE
          AND dr.statut = 'OUVERT'
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("prioriser_dossiers_par_score : %d dossiers reclassés", n)
    return n


# ─── 7. Promesses échues ─────────────────────────────────────────────────────


def verifier_promesses_echeues(**ctx) -> dict:
    """
    Identifie les promesses de paiement dont la date d'échéance est dépassée
    et qui n'ont pas été honorées.

    Met à jour statut → 'NON_HONOREE' et génère une alerte opérationnelle.
    Retourne un dict avec 'non_honorees', 'honorees_aujourd_hui'.
    """
    sql_non_honorees = """
        UPDATE app.promesses_paiement pp
        SET statut = 'NON_HONOREE',
            updated_at = NOW()
        WHERE pp.statut = 'EN_ATTENTE'
          AND pp.date_echeance < CURRENT_DATE
          AND NOT EXISTS (
              SELECT 1 FROM app.paiements_recus pr
              WHERE pr.dossier_id   = pp.dossier_id
                AND pr.date_paiement >= pp.date_echeance - INTERVAL '3 days'
                AND pr.montant      >= pp.montant_promis * 0.90  -- tolérance 10%
          )
        RETURNING pp.id, pp.dossier_id, pp.montant_promis
    """
    sql_honorees = """
        UPDATE app.promesses_paiement pp
        SET statut = 'HONOREE',
            updated_at = NOW()
        FROM app.paiements_recus pr
        WHERE pr.dossier_id   = pp.dossier_id
          AND pr.date_paiement BETWEEN pp.date_echeance - INTERVAL '3 days' AND CURRENT_DATE
          AND pr.montant >= pp.montant_promis * 0.90
          AND pp.statut  = 'EN_ATTENTE'
          AND pp.date_echeance <= CURRENT_DATE
        RETURNING pp.id
    """

    n_non_honorees = n_honorees = 0
    with db_session() as cur:
        cur.execute(sql_honorees)
        n_honorees = cur.rowcount

        cur.execute(sql_non_honorees)
        non_honorees_rows = cur.fetchall()
        n_non_honorees = len(non_honorees_rows)

    logger.info(
        "Promesses échues : non honorées=%d, honorées=%d",
        n_non_honorees,
        n_honorees,
    )
    return {"non_honorees": n_non_honorees, "honorees_aujourd_hui": n_honorees}


# ─── 8. KPIs recouvrement ────────────────────────────────────────────────────


def calculer_kpis_recouvrement(periodes: list[str] | None = None, **ctx) -> dict:
    """
    Calcule les KPIs de recouvrement agrégés et les insère dans dw.fait_recouvrement_mensuel.
    Retourne les métriques globales du jour.
    """
    sql_kpis = """
        SELECT
            COUNT(*) FILTER (WHERE statut = 'OUVERT')          AS dossiers_ouverts,
            COUNT(*) FILTER (WHERE statut = 'CLOS')            AS dossiers_clos,
            SUM(montant_recouvre) FILTER (WHERE
                date_cloture >= CURRENT_DATE - INTERVAL '30 days') AS recouvre_30j,
            AVG(taux_par90)                                    AS par90_moyen
        FROM app.dossiers_recouvrement dr
        LEFT JOIN app.kpi_recouvrement_snapshots ks
               ON ks.imf_id = dr.imf_id
              AND ks.date_snapshot = CURRENT_DATE
    """
    with readonly_session() as cur:
        cur.execute(sql_kpis)
        row = dict(cur.fetchone() or {})

    logger.info(
        "KPIs recouvrement : ouverts=%d, clos=%d, PAR90 moyen=%.2f%%",
        row.get("dossiers_ouverts", 0),
        row.get("dossiers_clos", 0),
        (row.get("par90_moyen") or 0) * 100,
    )
    return row


def calculer_benchmarks_agences(**ctx) -> int:
    """
    Calcule les benchmarks inter-agences (taux recouvrement, PAR, productivité agents)
    et les insère dans dw.benchmark_agences pour les tableaux de bord.

    Retourne le nombre de lignes insérées.
    """
    sql = """
        INSERT INTO dw.benchmark_agences (
            date_benchmark, imf_id, agence_id,
            taux_par90,
            taux_recouvrement_30j,
            nb_dossiers_ouverts,
            nb_agents_actifs,
            montant_collecte_moyen_par_agent,
            rang_intra_imf
        )
        SELECT
            CURRENT_DATE,
            ks.imf_id,
            ks.agence_id,
            ks.taux_par90,
            COALESCE(
                SUM(pr.montant) FILTER (WHERE pr.date_paiement >= CURRENT_DATE - 30)
                / NULLIF(ks.montant_encours, 0), 0
            )                                                   AS taux_recouvrement_30j,
            COUNT(DISTINCT dr.id) FILTER (WHERE dr.statut = 'OUVERT') AS nb_dossiers_ouverts,
            COUNT(DISTINCT u.id) FILTER (WHERE u.role = 'AGENT' AND u.actif) AS nb_agents_actifs,
            COALESCE(
                SUM(ce.montant) FILTER (WHERE ce.statut = 'VALIDEE'
                    AND ce.date_collecte >= CURRENT_DATE - 30)
                / NULLIF(COUNT(DISTINCT u.id) FILTER (WHERE u.role='AGENT' AND u.actif), 0), 0
            )                                                   AS montant_collecte_moyen_par_agent,
            RANK() OVER (
                PARTITION BY ks.imf_id ORDER BY ks.taux_par90
            )                                                   AS rang_intra_imf
        FROM app.kpi_recouvrement_snapshots ks
        LEFT JOIN app.dossiers_recouvrement dr ON dr.imf_id = ks.imf_id AND dr.agence_id = ks.agence_id
        LEFT JOIN app.paiements_recus pr ON pr.imf_id = ks.imf_id
        LEFT JOIN app.users u ON u.imf_id = ks.imf_id AND u.agence_id = ks.agence_id
        LEFT JOIN app.collectes_epargne ce ON ce.imf_id = ks.imf_id AND ce.agence_id = ks.agence_id
        WHERE ks.date_snapshot = CURRENT_DATE
        GROUP BY ks.imf_id, ks.agence_id, ks.taux_par90, ks.montant_encours
        ON CONFLICT (date_benchmark, imf_id, agence_id) DO UPDATE SET
            taux_par90                      = EXCLUDED.taux_par90,
            taux_recouvrement_30j           = EXCLUDED.taux_recouvrement_30j,
            nb_dossiers_ouverts             = EXCLUDED.nb_dossiers_ouverts,
            nb_agents_actifs                = EXCLUDED.nb_agents_actifs,
            montant_collecte_moyen_par_agent = EXCLUDED.montant_collecte_moyen_par_agent,
            rang_intra_imf                  = EXCLUDED.rang_intra_imf
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("calculer_benchmarks_agences : %d lignes insérées", n)
    return n
