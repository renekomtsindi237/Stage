"""
ingestion_utils.py
Utilitaires partagés pour les DAGs d'ingestion MTN et Orange.
Gestion CSV, hashing SHA-256, déduplication, journalisation.
"""
import hashlib
import logging
import os
import shutil
from datetime import datetime
from pathlib import Path
from typing import Optional

import pandas as pd
import psycopg2
from psycopg2.extras import execute_values

logger = logging.getLogger(__name__)

# ── Colonnes attendues dans les CSV ───────────────────────────────────────────

COLONNES_MTN = [
    "transaction_id", "date_transaction", "montant",
    "telephone_payeur", "nom_payeur", "reference_externe",
    "statut", "type_operation",
]

COLONNES_ORANGE = [
    "transaction_id", "date_transaction", "montant",
    "telephone_payeur", "nom_payeur", "reference_externe",
    "statut", "type_operation",
]

COLONNES_CBS = [
    "id_pret", "id_client", "nom_client", "telephone_client",
    "montant_pret", "date_deblocage", "date_echeance",
    "montant_rembourse", "solde_restant", "statut_pret",
    "nom_agence", "nom_produit", "nom_agent",
]


def get_connection() -> psycopg2.extensions.connection:
    """Retourne une connexion PostgreSQL depuis les variables d'environnement."""
    return psycopg2.connect(
        host=os.environ["POSTGRES_HOST"],
        port=int(os.environ.get("POSTGRES_PORT", 5432)),
        dbname=os.environ["POSTGRES_DB"],
        user=os.environ["POSTGRES_USER"],
        password=os.environ["POSTGRES_PASSWORD"],
    )


def calculer_hash_ligne(row: pd.Series) -> str:
    """Calcule le SHA-256 d'une ligne de DataFrame (toutes colonnes concaténées)."""
    contenu = "|".join(str(v) for v in row.values)
    return hashlib.sha256(contenu.encode("utf-8")).hexdigest()


def lire_csv_mtn(chemin_fichier: str) -> pd.DataFrame:
    """
    Lit un fichier CSV MTN et normalise les colonnes.
    Retourne un DataFrame avec les colonnes standards + hash_sha256.
    """
    try:
        df = pd.read_csv(
            chemin_fichier,
            sep=";",
            encoding="utf-8",
            dtype=str,
            skipinitialspace=True,
        )
    except UnicodeDecodeError:
        df = pd.read_csv(
            chemin_fichier,
            sep=";",
            encoding="latin-1",
            dtype=str,
            skipinitialspace=True,
        )

    df.columns = [c.strip().lower().replace(" ", "_") for c in df.columns]

    # Mapping flexible des colonnes MTN (les noms varient selon les versions)
    mapping = {
        "id_transaction": "transaction_id",
        "id": "transaction_id",
        "date": "date_transaction",
        "amount": "montant",
        "phone": "telephone_payeur",
        "msisdn": "telephone_payeur",
        "name": "nom_payeur",
        "reference": "reference_externe",
        "external_ref": "reference_externe",
        "status": "statut",
        "type": "type_operation",
    }
    df = df.rename(columns=mapping)

    for col in COLONNES_MTN:
        if col not in df.columns:
            df[col] = None

    df["hash_sha256"] = df[COLONNES_MTN].apply(calculer_hash_ligne, axis=1)
    df["nom_fichier_source"] = Path(chemin_fichier).name
    df = df.dropna(subset=["transaction_id"])

    logger.info("CSV MTN lu : %s lignes depuis %s", len(df), chemin_fichier)
    return df


def lire_csv_orange(chemin_fichier: str) -> pd.DataFrame:
    """
    Lit un fichier CSV Orange Money.
    Orange utilise souvent un délimiteur virgule et encodage UTF-8-BOM.
    """
    try:
        df = pd.read_csv(
            chemin_fichier,
            sep=",",
            encoding="utf-8-sig",
            dtype=str,
            skipinitialspace=True,
        )
    except Exception:
        df = pd.read_csv(
            chemin_fichier,
            sep=";",
            encoding="latin-1",
            dtype=str,
            skipinitialspace=True,
        )

    df.columns = [c.strip().lower().replace(" ", "_") for c in df.columns]

    mapping = {
        "txn_id": "transaction_id",
        "date_heure": "date_transaction",
        "datetime": "date_transaction",
        "montant_xaf": "montant",
        "msisdn_payeur": "telephone_payeur",
        "prenom_nom": "nom_payeur",
        "ref_paiement": "reference_externe",
        "etat": "statut",
        "nature": "type_operation",
    }
    df = df.rename(columns=mapping)

    for col in COLONNES_ORANGE:
        if col not in df.columns:
            df[col] = None

    df["hash_sha256"] = df[COLONNES_ORANGE].apply(calculer_hash_ligne, axis=1)
    df["nom_fichier_source"] = Path(chemin_fichier).name
    df = df.dropna(subset=["transaction_id"])

    logger.info("CSV Orange lu : %s lignes depuis %s", len(df), chemin_fichier)
    return df


def inserer_transactions(
    conn: psycopg2.extensions.connection,
    df: pd.DataFrame,
    table: str,
    colonnes: list[str],
    dag_run_id: str,
) -> dict:
    """
    Insère un DataFrame dans raw.<table> avec ON CONFLICT DO NOTHING.
    Retourne un dict {inserts, doublons, total}.
    """
    if df.empty:
        return {"inserts": 0, "doublons": 0, "total": 0}

    df = df.copy()
    df["dag_run_id"] = dag_run_id
    colonnes_insert = colonnes + ["hash_sha256", "nom_fichier_source", "dag_run_id"]

    rows = [
        tuple(row[col] if pd.notna(row.get(col)) else None for col in colonnes_insert)
        for _, row in df.iterrows()
    ]

    sql = f"""
        INSERT INTO {table} ({', '.join(colonnes_insert)})
        VALUES %s
        ON CONFLICT (hash_sha256) DO NOTHING
    """

    with conn.cursor() as cur:
        nb_avant = _count_table(cur, table)
        execute_values(cur, sql, rows, page_size=500)
        conn.commit()
        nb_apres = _count_table(cur, table)

    inserts = nb_apres - nb_avant
    doublons = len(df) - inserts

    logger.info(
        "Table %s — inserts: %d | doublons ignorés: %d | total CSV: %d",
        table, inserts, doublons, len(df),
    )
    return {"inserts": inserts, "doublons": doublons, "total": len(df)}


def _count_table(cur, table: str) -> int:
    cur.execute(f"SELECT COUNT(*) FROM {table}")
    return cur.fetchone()[0]


def journaliser_ingestion(
    conn: psycopg2.extensions.connection,
    dag_id: str,
    dag_run_id: str,
    nom_fichier: str,
    source: str,
    stats: dict,
    statut: str = "SUCCESS",
    message_erreur: Optional[str] = None,
    debut: Optional[datetime] = None,
) -> None:
    """Insère une entrée dans raw.journal_ingestions."""
    sql = """
        INSERT INTO raw.journal_ingestions
            (dag_id, dag_run_id, nom_fichier, source,
             nb_lignes_lues, nb_inserts, nb_doublons, nb_erreurs,
             statut, message_erreur, debut_ingestion)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """
    with conn.cursor() as cur:
        cur.execute(sql, (
            dag_id,
            dag_run_id,
            nom_fichier,
            source,
            stats.get("total", 0),
            stats.get("inserts", 0),
            stats.get("doublons", 0),
            stats.get("erreurs", 0),
            statut,
            message_erreur,
            debut or datetime.now(),
        ))
        conn.commit()


def archiver_fichier(chemin_source: str, dossier_archive: str) -> str:
    """Déplace le fichier traité dans le dossier d'archive avec timestamp."""
    Path(dossier_archive).mkdir(parents=True, exist_ok=True)
    nom = Path(chemin_source).stem
    ext = Path(chemin_source).suffix
    horodatage = datetime.now().strftime("%Y%m%d_%H%M%S")
    destination = Path(dossier_archive) / f"{nom}_{horodatage}{ext}"
    shutil.move(chemin_source, destination)
    logger.info("Fichier archivé : %s → %s", chemin_source, destination)
    return str(destination)


def lister_fichiers_a_traiter(dossier: str, pattern: str = "*.csv") -> list[str]:
    """Retourne la liste des fichiers CSV à ingérer dans le dossier source."""
    return sorted(str(p) for p in Path(dossier).glob(pattern))
