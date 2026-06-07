"""
ml_scoring_utils.py — Fonctions appelées par dag_ml_scoring.

Scoring MCRS journalier :
- Chargement du modèle actif depuis ml.model_runs.
- Scoring par batch de 500 clients avec MCRSModel.predict_batch().
- Calcul SHAP et insertion dans ml.shap_explanations.
- Détection drift PSI.
- Mise à jour des priorités de dossiers de recouvrement.
"""
from __future__ import annotations

import json
import logging
import os
import time
from pathlib import Path

import numpy as np
import pandas as pd

from pipeline.src.database import db_session, readonly_session
from pipeline.src.ml.feature_engineering import construire_features_scoring
from pipeline.src.ml.mcrs_model import MCRSModel, ScoreResult

logger = logging.getLogger(__name__)

MODEL_BASE_DIR = Path(os.getenv("MCRS_MODEL_DIR", "/ml/models/mcrs"))
CHAMPION_DIR   = MODEL_BASE_DIR / "champion"


def charger_modele_actif(**ctx) -> str:
    """
    Charge le modèle MCRS champion depuis le disque.
    Stocke le chemin dans XCom pour les tâches suivantes.
    """
    if not CHAMPION_DIR.exists():
        raise FileNotFoundError(
            f"Répertoire modèle champion introuvable : {CHAMPION_DIR}\n"
            "Exécutez dag_ml_training au moins une fois pour créer le premier modèle."
        )

    model = MCRSModel.charger(CHAMPION_DIR)
    logger.info(
        "Modèle MCRS champion chargé — AUC=%.4f",
        model.metrics_.get("auc_roc", 0),
    )

    ti = ctx.get("ti")
    if ti:
        ti.xcom_push(key="model_dir", value=str(CHAMPION_DIR))
        ti.xcom_push(key="model_auc", value=model.metrics_.get("auc_roc", 0))

    return str(CHAMPION_DIR)


def scorer_clients_batch(
    batch_size: int = 500,
    poids_crs: float = 0.35,
    poids_rps: float = 0.45,
    poids_csi: float = 0.20,
    **ctx,
) -> dict:
    """
    Score tous les clients actifs de toutes les IMF.

    Traitement :
    1. Récupère la liste des IMF actives.
    2. Pour chaque IMF, construit les features et score par batch.
    3. Insère les résultats dans ml.client_scores (upsert par client + date).
    4. Met à jour app.creances avec le score MCRS du jour.

    Returns
    -------
    dict avec 'total_clients', 'total_imfs', 'duration_ms'
    """
    from pipeline.src.ml.mcrs_model import McrsParams
    from pipeline.src.ml.feature_engineering import reconstruire_imf_ids_actifs

    t0 = time.perf_counter()

    # Charger le modèle
    model = MCRSModel.charger(CHAMPION_DIR)
    model.params.poids_crs = poids_crs
    model.params.poids_rps = poids_rps
    model.params.poids_csi = poids_csi

    imf_ids = reconstruire_imf_ids_actifs()
    logger.info("Scoring MCRS pour %d IMF actives", len(imf_ids))

    total_clients = 0
    all_scores: list[ScoreResult] = []

    for imf_id in imf_ids:
        try:
            df = construire_features_scoring(imf_id)
            if df.empty:
                logger.warning("Aucun client à scorer pour imf_id=%d", imf_id)
                continue

            # Traitement par batch
            for start in range(0, len(df), batch_size):
                chunk = df.iloc[start : start + batch_size]
                scores = model.predict_batch(chunk)
                all_scores.extend(scores)
                total_clients += len(scores)

            logger.info("IMF %d : %d clients scorés", imf_id, len(df))

        except Exception as exc:
            logger.error("Erreur scoring IMF %d : %s", imf_id, exc, exc_info=True)
            continue

    # Insertion en base
    if all_scores:
        _inserer_scores(all_scores)
        _maj_scores_creances(all_scores)

    # Stocker scores pour PSI
    ti = ctx.get("ti")
    if ti and all_scores:
        scores_array = [s.score_mcrs for s in all_scores]
        ti.xcom_push(key="scores_journaliers", value=scores_array)

    duration_ms = round((time.perf_counter() - t0) * 1000)
    logger.info(
        "Scoring terminé : %d clients, %d IMF, %.0f ms",
        total_clients, len(imf_ids), duration_ms,
    )
    return {
        "total_clients": total_clients,
        "total_imfs":    len(imf_ids),
        "duration_ms":   duration_ms,
    }


def calculer_shap_values(top_n_features: int = 10, **ctx) -> int:
    """
    Récupère les SHAP values de la dernière session de scoring
    et les insère dans ml.shap_explanations.

    Les SHAP values sont déjà calculées par predict_batch() et stockées
    dans ml.client_scores.shap_top_features (JSONB).
    Cette tâche les réécrit dans la table dédiée ml.shap_explanations.
    """
    sql_select = """
        SELECT client_id, imf_id, date_score, shap_top_features
        FROM ml.client_scores
        WHERE date_score = CURRENT_DATE
          AND shap_top_features IS NOT NULL
    """
    sql_insert = """
        INSERT INTO ml.shap_explanations
            (client_id, imf_id, date_score, feature_name, shap_value, rang)
        VALUES
            (%(client_id)s, %(imf_id)s, %(date_score)s,
             %(feature_name)s, %(shap_value)s, %(rang)s)
        ON CONFLICT (client_id, imf_id, date_score, feature_name) DO UPDATE
            SET shap_value = EXCLUDED.shap_value,
                rang       = EXCLUDED.rang
    """
    n_rows = 0
    with readonly_session() as cur:
        cur.execute(sql_select)
        rows = cur.fetchall()

    with db_session() as cur:
        for row in rows:
            shap_dict: dict = row["shap_top_features"] or {}
            for rang, (feat, val) in enumerate(
                sorted(shap_dict.items(), key=lambda x: abs(x[1]), reverse=True)[:top_n_features],
                start=1,
            ):
                cur.execute(sql_insert, {
                    "client_id":  row["client_id"],
                    "imf_id":     row["imf_id"],
                    "date_score": row["date_score"],
                    "feature_name": feat,
                    "shap_value": float(val),
                    "rang":       rang,
                })
                n_rows += 1

    logger.info("SHAP explanations insérées : %d lignes", n_rows)
    return n_rows


def detecter_drift_psi(
    fenetre_reference_jours: int = 90,
    fenetre_courante_jours: int = 7,
    **ctx,
) -> float:
    """
    Délègue au moniteur PSI segmenté (zone × produit).
    Conservé pour compatibilité avec d'éventuels appels directs.
    """
    return detecter_drift_psi_segmente(
        fenetre_reference_jours=fenetre_reference_jours,
        fenetre_courante_jours=fenetre_courante_jours,
        **ctx,
    )


def detecter_drift_psi_segmente(
    fenetre_reference_jours: int = 90,
    fenetre_courante_jours: int = 7,
    **ctx,
) -> float:
    """
    PSI par segment (zone_id × catégorie_produit) et PSI global.

    Un PSI global masque les drifts localisés : si une zone souffre d'une
    sécheresse ou d'un choc prix, ses scores dérivent mais sont compensés
    par les zones stables.  Cette fonction calcule le PSI pour chaque segment
    et retourne max(PSI_global, PSI_max_segment) pour décision de retrain.

    Seuils (inchangés) :
    - PSI < 0.10  : stable
    - 0.10–0.20   : surveillance
    - ≥ 0.20      : drift significatif → retrain déclenché
    """
    sql_scores = """
        SELECT
            cs.score_mcrs,
            cs.date_score,
            COALESCE(ci.zone_id, 'INCONNU')   AS zone_id,
            COALESCE(pg.categorie, 'AUTRE')   AS categorie_produit
        FROM ml.client_scores cs
        LEFT JOIN app.clients c
            ON  c.client_id_externe = cs.client_id_externe
            AND c.imf_id::TEXT       = cs.imf_code
        LEFT JOIN app.clients_informels ci
            ON  ci.client_id = c.id
        LEFT JOIN app.client_activites_produits cap
            ON  cap.client_informel_id = ci.id
            AND cap.est_activite_principale = TRUE
        LEFT JOIN app.produits_generiques pg
            ON  pg.id = cap.produit_id
        WHERE cs.date_score >= CURRENT_DATE - %(jours_ref)s * INTERVAL '1 day'
    """

    with readonly_session() as cur:
        cur.execute(sql_scores, {"jours_ref": fenetre_reference_jours})
        rows = cur.fetchall()

    ti = ctx.get("ti")

    if not rows:
        logger.warning("Aucune donnée pour PSI segmenté — skip drift")
        if ti:
            ti.xcom_push(key="psi", value=0.0)
            ti.xcom_push(key="psi_par_segment", value={})
        return 0.0

    df = pd.DataFrame(rows, columns=["score_mcrs", "date_score", "zone_id", "categorie_produit"])
    df["date_score"] = pd.to_datetime(df["date_score"])
    cutoff = pd.Timestamp.now().normalize() - pd.Timedelta(days=fenetre_courante_jours)

    df_ref = df[df["date_score"] < cutoff]
    df_cur = df[df["date_score"] >= cutoff]

    if len(df_ref) < 50 or len(df_cur) < 10:
        logger.warning(
            "Données globales insuffisantes pour PSI — ref=%d, cur=%d — skip",
            len(df_ref), len(df_cur),
        )
        if ti:
            ti.xcom_push(key="psi", value=0.0)
            ti.xcom_push(key="psi_par_segment", value={})
        return 0.0

    # PSI global
    psi_global = MCRSModel.calculer_psi(df_ref["score_mcrs"].values, df_cur["score_mcrs"].values)

    # PSI par segment (zone × catégorie_produit)
    psi_par_segment: dict[str, float] = {}
    segments_alertes: list[dict] = []

    for (zone, categorie), group_ref in df_ref.groupby(["zone_id", "categorie_produit"]):
        group_cur = df_cur[
            (df_cur["zone_id"] == zone) & (df_cur["categorie_produit"] == categorie)
        ]
        if len(group_ref) < 20 or len(group_cur) < 5:
            continue   # segment trop petit pour être fiable

        psi_seg = MCRSModel.calculer_psi(
            group_ref["score_mcrs"].values,
            group_cur["score_mcrs"].values,
        )
        segment_key = f"{zone}/{categorie}"
        psi_par_segment[segment_key] = psi_seg

        if psi_seg >= 0.20:
            segments_alertes.append({"segment": segment_key, "psi": round(psi_seg, 4)})
            logger.warning("DRIFT SEGMENTÉ — %s PSI=%.4f ≥ 0.20", segment_key, psi_seg)

    psi_max_segment = max(psi_par_segment.values()) if psi_par_segment else 0.0
    psi_final       = max(psi_global, psi_max_segment)

    logger.info(
        "PSI global=%.4f | max_segment=%.4f | %d segments analysés | psi_final=%.4f",
        psi_global, psi_max_segment, len(psi_par_segment), psi_final,
    )

    if ti:
        ti.xcom_push(key="psi", value=psi_final)
        ti.xcom_push(key="psi_par_segment", value=psi_par_segment)

    if psi_final >= 0.20:
        logger.warning("DRIFT DÉTECTÉ — PSI_final=%.4f ≥ 0.20 — retraining planifié", psi_final)
        _inserer_alerte_drift_segmentee(psi_final, psi_global, segments_alertes)

    return psi_final


def maj_priorites_dossiers_recouvrement(**ctx) -> int:
    """
    Met à jour la colonne priorite_scoring dans app.dossiers_recouvrement
    en fonction du score MCRS du jour pour chaque client.
    """
    sql = """
        UPDATE app.dossiers_recouvrement dr
        SET
            priorite_scoring = cs.priorite_recouvrement,
            score_mcrs_dernier = cs.score_mcrs,
            classe_risque_mcrs = cs.classe_risque,
            updated_at = NOW()
        FROM ml.client_scores cs
        JOIN app.clients c ON c.id = dr.client_id AND c.imf_id = dr.imf_id
        WHERE c.client_id_externe = cs.client_id_externe
          AND cs.imf_code = dr.imf_id::TEXT
          AND cs.date_score = CURRENT_DATE
          AND dr.statut = 'OUVERT'
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("Priorités dossiers mis à jour : %d dossiers", n)
    return n


# ─── Helpers privés ───────────────────────────────────────────────────────────

def _inserer_scores(scores: list[ScoreResult]) -> None:
    sql = """
        INSERT INTO ml.client_scores (
            client_id_externe, imf_code, date_score,
            score_crs, score_rps, score_csi, score_mcrs,
            classe_risque, probabilite_defaut_30j, probabilite_defaut_90j,
            score_mcrs_ic_bas, score_mcrs_ic_haut,
            action_recommandee, priorite_recouvrement,
            shap_top_features, scored_at
        ) VALUES (
            %(client_id_externe)s, %(imf_code)s, CURRENT_DATE,
            %(score_crs)s, %(score_rps)s, %(score_csi)s, %(score_mcrs)s,
            %(classe_risque)s, %(probabilite_defaut_30j)s, %(probabilite_defaut_90j)s,
            %(score_mcrs_ic_bas)s, %(score_mcrs_ic_haut)s,
            %(action_recommandee)s, %(priorite_recouvrement)s,
            %(shap_top_features)s, NOW()
        )
        ON CONFLICT (client_id_externe, imf_code, date_score)
        DO UPDATE SET
            score_crs                = EXCLUDED.score_crs,
            score_rps                = EXCLUDED.score_rps,
            score_csi                = EXCLUDED.score_csi,
            score_mcrs               = EXCLUDED.score_mcrs,
            classe_risque            = EXCLUDED.classe_risque,
            probabilite_defaut_30j   = EXCLUDED.probabilite_defaut_30j,
            probabilite_defaut_90j   = EXCLUDED.probabilite_defaut_90j,
            action_recommandee       = EXCLUDED.action_recommandee,
            priorite_recouvrement    = EXCLUDED.priorite_recouvrement,
            shap_top_features        = EXCLUDED.shap_top_features,
            scored_at                = EXCLUDED.scored_at
    """
    with db_session() as cur:
        for score in scores:
            cur.execute(sql, {
                **score.to_dict(),
                "shap_top_features": json.dumps(score.shap_values),
            })


def _maj_scores_creances(scores: list[ScoreResult]) -> None:
    sql = """
        UPDATE app.creances cr
        SET
            score_mcrs        = %(score_mcrs)s,
            score_crs         = %(score_crs)s,
            score_rps         = %(score_rps)s,
            score_csi         = %(score_csi)s,
            classe_risque_mcrs= %(classe_risque)s,
            updated_at        = NOW()
        FROM app.clients c
        WHERE c.id              = cr.client_id
          AND c.imf_id          = cr.imf_id
          AND c.client_id_externe = %(client_id_externe)s
    """
    with db_session() as cur:
        for score in scores:
            cur.execute(sql, {
                "score_mcrs":          score.score_mcrs,
                "score_crs":           score.score_crs,
                "score_rps":           score.score_rps,
                "score_csi":           score.score_csi,
                "classe_risque":       score.classe_risque,
                "client_id_externe":   score.client_id_externe,
            })


def _inserer_alerte_drift(psi: float) -> None:
    _inserer_alerte_drift_segmentee(psi, psi, [])


def _inserer_alerte_drift_segmentee(
    psi_final: float,
    psi_global: float,
    segments_alertes: list[dict],
) -> None:
    """Insère une alerte de drift avec détail par segment dans ml.alertes_predictives."""
    msg = (
        f"Drift PSI_final={psi_final:.4f} (global={psi_global:.4f})"
        + (f" — segments: {json.dumps(segments_alertes)}" if segments_alertes else "")
    )
    sql = """
        INSERT INTO ml.alertes_predictives
            (type_alerte, message, psi_valeur, date_detection, statut, created_at)
        VALUES
            ('DRIFT_DETECTE', %(message)s, %(psi)s, CURRENT_DATE, 'ACTIVE', NOW())
        ON CONFLICT DO NOTHING
    """
    with db_session() as cur:
        cur.execute(sql, {"message": msg, "psi": psi_final})
