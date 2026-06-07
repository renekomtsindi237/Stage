"""
IMF Pipeline — Utilitaires scoring MCRS pour dag_ml_scoring
============================================================

Fonctions appelées par les tâches PythonOperator du DAG dag_ml_scoring.
Orchestrent le chargement du modèle, le scoring batch, les SHAP values
et la détection de drift PSI.
"""
from __future__ import annotations

import json
import logging
import os
import pickle
import time
from datetime import datetime, timedelta
from typing import Any

import numpy as np

log = logging.getLogger("imf.ml.scoring")

MCRS_MODEL_DIR   = os.environ.get("MCRS_MODEL_DIR", "/ml/models/mcrs")
POSTGRES_HOST    = os.environ.get("POSTGRES_HOST", "localhost")
POSTGRES_PORT    = os.environ.get("POSTGRES_PORT", "5432")
POSTGRES_DB      = os.environ.get("POSTGRES_DB", "imf_dev")
POSTGRES_USER    = os.environ.get("POSTGRES_USER", "imf")
POSTGRES_PASSWORD = os.environ.get("POSTGRES_PASSWORD", "imf_pass")

# Poids MCRS
W_CRS, W_RPS, W_CSI = 0.35, 0.45, 0.20

# Seuils COBAC CEMAC
COBAC_SEUILS = [(0, "A"), (30, "B"), (90, "C"), (180, "D"), (360, "E")]


def _get_pg_conn():
    import psycopg2
    return psycopg2.connect(
        host=POSTGRES_HOST, port=POSTGRES_PORT,
        dbname=POSTGRES_DB, user=POSTGRES_USER, password=POSTGRES_PASSWORD,
        connect_timeout=10,
    )


def _cobac_classe(jours_retard: int) -> tuple[str, float]:
    """Retourne (classe, taux_provision) selon COBAC EMF 01/02."""
    provisions = {"A": 0.00, "B": 0.20, "C": 0.50, "D": 0.80, "E": 1.00}
    classe = "A"
    for seuil, code in sorted(COBAC_SEUILS, reverse=True):
        if jours_retard >= seuil:
            classe = code
            break
    return classe, provisions[classe]


def charger_modele_actif(**ctx) -> dict[str, Any]:
    """
    Charge le modèle MCRS actif depuis ml.model_runs.
    Pousse le path du modèle dans XCom pour les tâches suivantes.
    """
    conn = _get_pg_conn()
    cur = conn.cursor()
    cur.execute("""
        SELECT id, version, artifact_path, params_json
        FROM ml.model_runs
        WHERE est_modele_actif = TRUE
        ORDER BY created_at DESC
        LIMIT 1
    """)
    row = cur.fetchone()
    cur.close()
    conn.close()

    if row is None:
        raise ValueError("Aucun modèle actif dans ml.model_runs. "
                         "Lancer dag_ml_training en premier.")

    model_id, version, artifact_path, params = row
    log.info("Modele actif charge — id=%d version=%s path=%s", model_id, version, artifact_path)

    ctx["ti"].xcom_push(key="model_id", value=model_id)
    ctx["ti"].xcom_push(key="model_version", value=version)
    ctx["ti"].xcom_push(key="artifact_path", value=artifact_path)
    return {"model_id": model_id, "version": version}


def scorer_clients_batch(batch_size: int = 500,
                          poids_crs: float = W_CRS,
                          poids_rps: float = W_RPS,
                          poids_csi: float = W_CSI,
                          **ctx) -> int:
    """
    Score tous les clients actifs en batch.
    Upsert dans ml.client_scores + publie sur Kafka si disponible.
    """
    from pipeline.compute_mcrs import MCRSScorer

    artifact_path = ctx["ti"].xcom_pull(task_ids="charger_modele", key="artifact_path")
    model_version = ctx["ti"].xcom_pull(task_ids="charger_modele", key="model_version")
    model_id      = ctx["ti"].xcom_pull(task_ids="charger_modele", key="model_id")

    # Charge le scorer avec le modèle sérialisé
    scorer = MCRSScorer.from_pickle(
        os.path.join(artifact_path or MCRS_MODEL_DIR, "mcrs_model.pkl")
    )

    conn = _get_pg_conn()
    cur = conn.cursor()

    # Charge les features depuis ml.features_client (dernière période)
    cur.execute("""
        SELECT *
        FROM ml.features_client
        WHERE (imf_id, client_id_externe, computed_at) IN (
            SELECT imf_id, client_id_externe, MAX(computed_at)
            FROM ml.features_client
            GROUP BY imf_id, client_id_externe
        )
    """)
    cols = [d[0] for d in cur.description]
    rows = cur.fetchall()

    n_scored = 0
    for i in range(0, len(rows), batch_size):
        batch = [dict(zip(cols, r)) for r in rows[i:i + batch_size]]
        for row_dict in batch:
            try:
                result = scorer.score(row_dict)
                jours_retard = int(row_dict.get("jours_retard_actuel", 0) or 0)
                cobac_classe, cobac_taux = _cobac_classe(jours_retard)

                cur.execute("""
                    INSERT INTO ml.client_scores
                        (client_id_externe, imf_id, score_crs, score_rps, score_csi,
                         score_mcrs, niveau_risque, cobac_classe, cobac_provision_taux,
                         model_version, scored_at, updated_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW())
                    ON CONFLICT (client_id_externe, imf_id) DO UPDATE SET
                        score_crs            = EXCLUDED.score_crs,
                        score_rps            = EXCLUDED.score_rps,
                        score_csi            = EXCLUDED.score_csi,
                        score_mcrs           = EXCLUDED.score_mcrs,
                        niveau_risque        = EXCLUDED.niveau_risque,
                        cobac_classe         = EXCLUDED.cobac_classe,
                        cobac_provision_taux = EXCLUDED.cobac_provision_taux,
                        model_version        = EXCLUDED.model_version,
                        scored_at            = EXCLUDED.scored_at,
                        updated_at           = EXCLUDED.updated_at
                """, (
                    row_dict["client_id_externe"],
                    row_dict["imf_id"],
                    result.get("score_crs", 0),
                    result.get("score_rps", 0),
                    result.get("score_csi", 0),
                    result.get("score_mcrs", 0),
                    result.get("niveau_risque", "FAIBLE"),
                    cobac_classe,
                    cobac_taux,
                    model_version or "1.0.0",
                ))
                n_scored += 1
            except Exception as e:
                log.warning("Erreur scoring client %s : %s",
                            row_dict.get("client_id_externe"), e)

        conn.commit()
        log.info("Batch %d-%d score — %d clients", i, i + len(batch), n_scored)

    cur.close()
    conn.close()
    log.info("Scoring termine — %d clients scores", n_scored)
    ctx["ti"].xcom_push(key="n_scored", value=n_scored)
    return n_scored


def calculer_shap_values(top_n_features: int = 10, **ctx) -> None:
    """
    Calcule les valeurs SHAP pour les clients scorés et les persiste dans ml.shap_explanations.
    Utilise SHAP TreeExplainer sur le modèle XGBoost RPS.
    """
    try:
        import shap
    except ImportError:
        log.warning("SHAP non disponible — calcul des explications ignoré")
        return

    from pipeline.compute_mcrs import MCRSScorer

    artifact_path = ctx["ti"].xcom_pull(task_ids="charger_modele", key="artifact_path")
    scorer = MCRSScorer.from_pickle(
        os.path.join(artifact_path or MCRS_MODEL_DIR, "mcrs_model.pkl")
    )

    if not hasattr(scorer, "_rps_model") or scorer._rps_model is None:
        log.warning("Modele RPS non disponible — SHAP ignore")
        return

    conn = _get_pg_conn()
    cur = conn.cursor()

    # Récupère les scores du jour avec leurs features
    cur.execute("""
        SELECT cs.id, cs.client_id_externe, cs.imf_id, fc.*
        FROM ml.client_scores cs
        JOIN ml.features_client fc
          ON fc.client_id_externe = cs.client_id_externe
         AND fc.imf_id = cs.imf_id
        WHERE cs.scored_at >= NOW() - INTERVAL '2 hours'
        LIMIT 5000
    """)
    cols = [d[0] for d in cur.description]
    rows = cur.fetchall()

    if not rows:
        log.info("Aucun score recent pour le calcul SHAP")
        cur.close()
        conn.close()
        return

    explainer = shap.TreeExplainer(scorer._rps_model)

    for row_data in rows:
        row = dict(zip(cols, row_data))
        score_id = row["id"]

        try:
            feature_names = scorer.RPS_FEATURES + scorer.CAMEROON_FEATURES
            X = np.array([[row.get(f, 0) for f in feature_names]])
            shap_vals = explainer.shap_values(X)[0]

            pairs = sorted(zip(feature_names, shap_vals),
                           key=lambda x: abs(x[1]), reverse=True)[:top_n_features]

            for rang, (fname, shap_val) in enumerate(pairs, 1):
                cur.execute("""
                    INSERT INTO ml.shap_explanations
                        (score_id, feature_name, shap_value, feature_value,
                         rang_importance, signe, created_at)
                    VALUES (%s, %s, %s, %s, %s, %s, NOW())
                    ON CONFLICT DO NOTHING
                """, (
                    score_id, fname, float(shap_val),
                    str(row.get(fname, "")),
                    rang, "+" if shap_val > 0 else "-",
                ))
        except Exception as e:
            log.warning("SHAP erreur client %s : %s", row.get("client_id_externe"), e)

    conn.commit()
    cur.close()
    conn.close()
    log.info("SHAP values calculees pour %d clients", len(rows))


def detecter_drift_psi(fenetre_reference_jours: int = 90,
                        fenetre_courante_jours: int = 7,
                        **ctx) -> float:
    """
    Calcule le PSI (Population Stability Index) pour détecter le drift du modèle.
    Retourne le PSI et le pousse en XCom. Seuil de retrain : PSI > 0.20.
    """
    conn = _get_pg_conn()
    cur = conn.cursor()

    # Distribution des scores dans la fenêtre de référence (90j passés)
    cur.execute("""
        SELECT niveau_risque, COUNT(*) AS n
        FROM ml.client_scores
        WHERE scored_at BETWEEN NOW() - INTERVAL '%s days' AND NOW() - INTERVAL '%s days'
        GROUP BY niveau_risque
    """ % (fenetre_reference_jours, fenetre_courante_jours))
    ref = {r[0]: r[1] for r in cur.fetchall()}

    # Distribution des scores courants (7 derniers jours)
    cur.execute("""
        SELECT niveau_risque, COUNT(*) AS n
        FROM ml.client_scores
        WHERE scored_at >= NOW() - INTERVAL '%s days'
        GROUP BY niveau_risque
    """ % fenetre_courante_jours)
    cur_dist = {r[0]: r[1] for r in cur.fetchall()}
    cur.close()
    conn.close()

    niveaux = ["FAIBLE", "MODERE", "ELEVE", "CRITIQUE"]
    total_ref = sum(ref.values()) or 1
    total_cur = sum(cur_dist.values()) or 1

    psi = 0.0
    for niveau in niveaux:
        p_ref = ref.get(niveau, 0.5) / total_ref
        p_cur = cur_dist.get(niveau, 0.5) / total_cur
        # Éviter log(0)
        p_ref = max(p_ref, 0.0001)
        p_cur = max(p_cur, 0.0001)
        psi += (p_cur - p_ref) * np.log(p_cur / p_ref)

    log.info("PSI calcule : %.4f (seuil retrain=0.20)", psi)
    ctx["ti"].xcom_push(key="psi", value=psi)

    if psi > 0.20:
        log.warning("DRIFT DETECTE — PSI=%.4f > 0.20 → retrain programme", psi)
    elif psi > 0.10:
        log.info("PSI moderement eleve (%.4f) — surveillance recommandee", psi)

    return psi


def maj_priorites_dossiers_recouvrement(**ctx) -> int:
    """
    Met à jour le champ priorite_scoring dans app.dossiers_recouvrement
    selon le score MCRS du jour (plus le score est élevé, plus la priorité est haute).
    """
    conn = _get_pg_conn()
    cur = conn.cursor()

    cur.execute("""
        UPDATE app.dossiers_recouvrement dr
        SET
            priorite_scoring = CASE cs.niveau_risque
                WHEN 'CRITIQUE' THEN 1
                WHEN 'ELEVE'    THEN 2
                WHEN 'MODERE'   THEN 3
                ELSE                 5
            END,
            updated_at = NOW()
        FROM ml.client_scores cs
        JOIN app.clients_informels c
          ON c.client_id_externe = cs.client_id_externe
         AND c.imf_id = (SELECT id FROM app.imf WHERE id = cs.imf_id LIMIT 1)
        WHERE dr.client_id = c.id
          AND dr.statut NOT IN ('CLOTURE', 'ANNULE')
    """)
    n = cur.rowcount
    conn.commit()
    cur.close()
    conn.close()
    log.info("Priorites recouvrement mises a jour — %d dossiers", n)
    return n
