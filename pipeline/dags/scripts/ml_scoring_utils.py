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

import pandas as pd

from pipeline.src.database import db_session, readonly_session
from pipeline.src.ml.feature_engineering import construire_features_scoring
from pipeline.src.ml.mcrs_model import MCRSModel, ScoreResult

logger = logging.getLogger(__name__)

MODEL_BASE_DIR = Path(os.getenv("MCRS_MODEL_DIR", "/ml/models/mcrs"))
CHAMPION_DIR = MODEL_BASE_DIR / "champion"

# Nombre de features SHAP conservées par score (les plus influentes, |valeur| décroissante)
SHAP_TOP_N = 10


def charger_modele_actif(**ctx) -> str:
    """
    Charge le modèle MCRS champion depuis le disque.
    Stocke le chemin, l'AUC et l'identité du run actif (ml.model_runs) dans XCom.
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

    model_run_id, model_version = _identite_run_actif()

    ti = ctx.get("ti")
    if ti:
        ti.xcom_push(key="model_dir", value=str(CHAMPION_DIR))
        ti.xcom_push(key="model_auc", value=model.metrics_.get("auc_roc", 0))
        ti.xcom_push(key="model_run_id", value=model_run_id)
        ti.xcom_push(key="model_version", value=model_version)

    return str(CHAMPION_DIR)


def _identite_run_actif() -> tuple[int | None, str]:
    """
    Résout le run ml.model_runs actif (est_modele_actif=TRUE le plus récent).

    Ne bloque jamais le scoring : (None, "inconnue") si aucun run n'est
    enregistré (ex. modèle déployé manuellement hors DAG, cf.
    pipeline/promouvoir_modele.py, qui n'écrit pas dans ml.model_runs).

    NB : la colonne `version` est aujourd'hui une constante statique
    ("2.0.0") côté dag_ml_training (ml_training_utils.py) — limite connue,
    hors périmètre de cette correction. `model_run_id` reste un identifiant
    fiable pour distinguer deux runs même tant que `version` ne le permet pas.
    """
    sql = """
        SELECT id, version FROM ml.model_runs
        WHERE est_modele_actif = TRUE
        ORDER BY created_at DESC LIMIT 1
    """
    with readonly_session() as cur:
        cur.execute(sql)
        row = cur.fetchone()
    if not row:
        return None, "inconnue"
    return row["id"], row["version"]


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
    3. Insère les résultats dans ml.client_scores (upsert par client, cf. V29 —
       une seule ligne courante par (client_id_externe, imf_id), pas par jour).
    4. Met à jour app.creances avec le score MCRS courant.

    Returns
    -------
    dict avec 'total_clients', 'total_imfs', 'duration_ms'
    """
    from pipeline.src.ml.feature_engineering import reconstruire_imf_ids_actifs

    t0 = time.perf_counter()

    # Charger le modèle
    model = MCRSModel.charger(CHAMPION_DIR)
    model.params.poids_crs = poids_crs
    model.params.poids_rps = poids_rps
    model.params.poids_csi = poids_csi

    model_run_id, model_version = _identite_run_actif()

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
        _inserer_scores(all_scores, model_run_id, model_version)
        _maj_scores_creances(all_scores)

    # Stocker scores pour PSI
    ti = ctx.get("ti")
    if ti and all_scores:
        scores_array = [s.score_mcrs for s in all_scores]
        ti.xcom_push(key="scores_journaliers", value=scores_array)

    duration_ms = round((time.perf_counter() - t0) * 1000)
    logger.info(
        "Scoring terminé : %d clients, %d IMF, %.0f ms",
        total_clients,
        len(imf_ids),
        duration_ms,
    )
    return {
        "total_clients": total_clients,
        "total_imfs": len(imf_ids),
        "duration_ms": duration_ms,
    }


def calculer_shap_values(top_n_features: int = 10, **ctx) -> int:
    """
    No-op de compatibilité, conservé comme tâche Airflow à part entière
    (dag_ml_scoring.py) pour ne pas modifier le graphe de tâches du DAG.

    Les valeurs SHAP sont désormais insérées directement par _inserer_scores()
    dans ml.shap_explanations, au moment où score.shap_values est encore en
    mémoire (juste après le calcul). L'ancienne implémentation tentait de les
    relire depuis une colonne ml.client_scores.shap_top_features qui n'a
    jamais existé dans le schéma réellement migré (V23/V29) — cette tâche
    aurait donc toujours échoué silencieusement (0 ligne, aucune erreur) si
    le pipeline avait pu être déclenché jusqu'ici.
    """
    logger.info("calculer_shap_values : no-op — SHAP déjà inséré par _inserer_scores().")
    return 0


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
            cs.scored_at                       AS date_score,
            COALESCE(ci.zone_id, 'INCONNU')     AS zone_id,
            COALESCE(pg.categorie, 'AUTRE')     AS categorie_produit
        FROM ml.client_scores cs
        LEFT JOIN app.clients_informels ci
            ON  ci.client_id_externe = cs.client_id_externe
            AND ci.imf_id            = cs.imf_id
        LEFT JOIN app.client_activites_produits cap
            ON  cap.client_id = ci.id
            AND cap.est_produit_principal = TRUE
        LEFT JOIN app.produits_generiques pg
            ON  pg.id = cap.produit_id
        WHERE cs.scored_at >= CURRENT_DATE - %(jours_ref)s * INTERVAL '1 day'
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

    df = pd.DataFrame(
        rows, columns=["score_mcrs", "date_score", "zone_id", "categorie_produit"]
    )
    # cs.scored_at est TIMESTAMPTZ -> pd.to_datetime infère un dtype tz-aware
    # (UTC) ; comparer à un Timestamp naïf lève TypeError, pas seulement un
    # résultat silencieusement faux — cutoff doit être tz-aware lui aussi.
    df["date_score"] = pd.to_datetime(df["date_score"], utc=True)
    cutoff = pd.Timestamp.now(tz="UTC").normalize() - pd.Timedelta(days=fenetre_courante_jours)

    df_ref = df[df["date_score"] < cutoff]
    df_cur = df[df["date_score"] >= cutoff]

    if len(df_ref) < 50 or len(df_cur) < 10:
        logger.warning(
            "Données globales insuffisantes pour PSI — ref=%d, cur=%d — skip",
            len(df_ref),
            len(df_cur),
        )
        if ti:
            ti.xcom_push(key="psi", value=0.0)
            ti.xcom_push(key="psi_par_segment", value={})
        return 0.0

    # PSI global
    psi_global = MCRSModel.calculer_psi(
        df_ref["score_mcrs"].values, df_cur["score_mcrs"].values
    )

    # PSI par segment (zone × catégorie_produit)
    psi_par_segment: dict[str, float] = {}
    segments_alertes: list[dict] = []

    for (zone, categorie), group_ref in df_ref.groupby(
        ["zone_id", "categorie_produit"]
    ):
        group_cur = df_cur[
            (df_cur["zone_id"] == zone) & (df_cur["categorie_produit"] == categorie)
        ]
        if len(group_ref) < 20 or len(group_cur) < 5:
            continue  # segment trop petit pour être fiable

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
    psi_final = max(psi_global, psi_max_segment)

    logger.info(
        "PSI global=%.4f | max_segment=%.4f | %d segments analysés | psi_final=%.4f",
        psi_global,
        psi_max_segment,
        len(psi_par_segment),
        psi_final,
    )

    if ti:
        ti.xcom_push(key="psi", value=psi_final)
        ti.xcom_push(key="psi_par_segment", value=psi_par_segment)

    if psi_final >= 0.20:
        logger.warning(
            "DRIFT DÉTECTÉ — PSI_final=%.4f ≥ 0.20 — retraining planifié", psi_final
        )
        _inserer_alerte_drift_segmentee(psi_final, psi_global, segments_alertes)

    return psi_final


def maj_priorites_dossiers_recouvrement(**ctx) -> int:
    """
    Met à jour la colonne priorite_scoring dans app.dossiers_recouvrement
    en fonction du score MCRS courant de chaque client.

    app.dossiers_recouvrement n'a pas de lien direct vers un client (ni
    client_id, ni client_id_externe) — seul app.creances porte les deux
    (via dossier_recouvrement_id). Le passage par app.creances est donc
    obligatoire, pas une simplification.
    """
    sql = """
        UPDATE app.dossiers_recouvrement dr
        SET
            priorite_scoring = cs.priorite_recouvrement,
            updated_at = NOW()
        FROM app.creances cr
        JOIN ml.client_scores cs
            ON  cs.client_id_externe = cr.client_id_externe
            AND cs.imf_id            = cr.imf_id
        WHERE cr.dossier_recouvrement_id = dr.id
          AND dr.clos = FALSE
    """
    with db_session() as cur:
        cur.execute(sql)
        n = cur.rowcount

    logger.info("Priorités dossiers mis à jour : %d dossiers", n)
    return n


# ─── Helpers privés ───────────────────────────────────────────────────────────


def _inserer_scores(
    scores: list[ScoreResult],
    model_run_id: int | None,
    model_version: str,
) -> None:
    """
    Upsert dans ml.client_scores (une ligne par client/IMF, cf. V29) puis,
    pour chaque score inséré/mis à jour, réinsertion complète de ses
    explications SHAP dans ml.shap_explanations (score_id FK — pas de colonne
    JSONB shap_top_features sur client_scores, contrairement à ce que
    l'ancienne implémentation supposait).
    """
    sql_score = """
        INSERT INTO ml.client_scores (
            imf_id, client_id_externe, model_run_id, model_version,
            score_crs, score_rps, score_csi, score_mcrs,
            niveau_risque, probabilite_defaut_30j, probabilite_defaut_90j,
            score_mcrs_ic_bas, score_mcrs_ic_haut,
            action_recommandee, priorite_recouvrement, scored_at
        )
        SELECT
            i.id, %(client_id_externe)s, %(model_run_id)s, %(model_version)s,
            %(score_crs)s, %(score_rps)s, %(score_csi)s, %(score_mcrs)s,
            %(classe_risque)s, %(probabilite_defaut_30j)s, %(probabilite_defaut_90j)s,
            %(score_mcrs_ic_bas)s, %(score_mcrs_ic_haut)s,
            %(action_recommandee)s, %(priorite_recouvrement)s, NOW()
        FROM app.imf i WHERE i.code = %(imf_code)s
        ON CONFLICT (client_id_externe, imf_id)
        DO UPDATE SET
            model_run_id             = EXCLUDED.model_run_id,
            model_version            = EXCLUDED.model_version,
            score_crs                = EXCLUDED.score_crs,
            score_rps                = EXCLUDED.score_rps,
            score_csi                = EXCLUDED.score_csi,
            score_mcrs                = EXCLUDED.score_mcrs,
            niveau_risque             = EXCLUDED.niveau_risque,
            probabilite_defaut_30j    = EXCLUDED.probabilite_defaut_30j,
            probabilite_defaut_90j    = EXCLUDED.probabilite_defaut_90j,
            score_mcrs_ic_bas         = EXCLUDED.score_mcrs_ic_bas,
            score_mcrs_ic_haut        = EXCLUDED.score_mcrs_ic_haut,
            action_recommandee        = EXCLUDED.action_recommandee,
            priorite_recouvrement     = EXCLUDED.priorite_recouvrement,
            scored_at                 = EXCLUDED.scored_at,
            updated_at                = NOW()
        RETURNING id
    """
    sql_purge_shap = "DELETE FROM ml.shap_explanations WHERE score_id = %(score_id)s"
    sql_shap = """
        INSERT INTO ml.shap_explanations
            (score_id, feature_name, shap_value, rang_importance, signe)
        VALUES (%(score_id)s, %(feature_name)s, %(shap_value)s, %(rang)s, %(signe)s)
    """
    with db_session() as cur:
        for score in scores:
            params = {
                **score.to_dict(),
                "model_run_id": model_run_id,
                "model_version": model_version,
            }
            cur.execute(sql_score, params)
            row = cur.fetchone()
            if row is None:
                logger.error(
                    "Code IMF inconnu '%s' — score de %s non inséré",
                    score.imf_code,
                    score.client_id_externe,
                )
                continue
            score_id = row["id"]

            # Repartir propre : évite d'accumuler des lignes SHAP obsolètes
            # si le nombre/l'ordre des features top-N change entre deux runs.
            cur.execute(sql_purge_shap, {"score_id": score_id})

            top_features = sorted(
                score.shap_values.items(), key=lambda kv: abs(kv[1]), reverse=True
            )[:SHAP_TOP_N]
            for rang, (feat, val) in enumerate(top_features, start=1):
                cur.execute(
                    sql_shap,
                    {
                        "score_id": score_id,
                        "feature_name": feat,
                        "shap_value": float(val),
                        "rang": rang,
                        "signe": "+" if val >= 0 else "-",
                    },
                )


def _maj_scores_creances(scores: list[ScoreResult]) -> None:
    """
    Miroir du score MCRS courant sur app.creances (colonnes ajoutées par
    V60 — absentes du schéma jusque-là, cf. commentaire de la migration).
    """
    sql = """
        UPDATE app.creances cr
        SET
            score_mcrs          = %(score_mcrs)s,
            score_crs            = %(score_crs)s,
            score_rps             = %(score_rps)s,
            score_csi             = %(score_csi)s,
            classe_risque_mcrs    = %(classe_risque)s,
            updated_at            = NOW()
        FROM app.imf i
        WHERE i.code = %(imf_code)s
          AND cr.imf_id = i.id
          AND cr.client_id_externe = %(client_id_externe)s
    """
    with db_session() as cur:
        for score in scores:
            cur.execute(
                sql,
                {
                    "score_mcrs": score.score_mcrs,
                    "score_crs": score.score_crs,
                    "score_rps": score.score_rps,
                    "score_csi": score.score_csi,
                    "classe_risque": score.classe_risque,
                    "imf_code": score.imf_code,
                    "client_id_externe": score.client_id_externe,
                },
            )


def _inserer_alerte_drift(psi: float) -> None:
    _inserer_alerte_drift_segmentee(psi, psi, [])


def _inserer_alerte_drift_segmentee(
    psi_final: float,
    psi_global: float,
    segments_alertes: list[dict],
) -> None:
    """
    Journalise un drift détecté (log uniquement — pas d'écriture en base).

    ml.alertes_predictives exige imf_id, client_id_externe et titre NOT NULL,
    et son CHECK type_alerte ne couvre que des alertes par client (pas de
    valeur "drift de portefeuille"). Un drift PSI est par nature un
    phénomène de portefeuille/segment, pas rattaché à un client précis —
    y écrire forcerait soit une valeur de type_alerte hors contrainte, soit
    un client_id_externe fictif trompeur. Tant qu'aucune table dédiée au
    suivi de drift n'existe (hors périmètre de cette correction), le log
    Airflow (conservé par la rétention des logs du scheduler) reste la
    seule trace — visible dans les logs de la tâche detecter_drift.
    """
    msg = f"Drift PSI_final={psi_final:.4f} (global={psi_global:.4f})" + (
        f" — segments: {json.dumps(segments_alertes)}" if segments_alertes else ""
    )
    logger.warning("ALERTE DRIFT (non persistée en base — cf. docstring) : %s", msg)
