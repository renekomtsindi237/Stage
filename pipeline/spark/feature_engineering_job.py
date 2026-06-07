"""
IMF Pipeline — Spark Feature Engineering Job
============================================

Job PySpark soumis via spark-submit sur le cluster Spark standalone.
Calcule les features ML complètes (30 variables) pour tous les clients actifs
à partir des tables PostgreSQL, puis écrit dans la vue matérialisée ml.features_client.

Usage (via spark-submit dans docker-compose.analytics.yml) :
  spark-submit \
    --master spark://spark-master:7077 \
    --jars /opt/bitnami/spark/jars/postgresql-42.7.3.jar \
    /opt/spark-apps/feature_engineering_job.py

Schedule : DAG Airflow dag_ml_scoring.py (quotidien à 02h00 CAT)
"""
from __future__ import annotations

import logging
import os
import sys
from datetime import date, timedelta

log = logging.getLogger("imf.spark.feature_engineering")

# ─── Paramètres d'environnement ───────────────────────────────────────────────

POSTGRES_HOST     = os.environ.get("POSTGRES_HOST", "postgres")
POSTGRES_PORT     = os.environ.get("POSTGRES_PORT", "5432")
POSTGRES_DB       = os.environ.get("POSTGRES_DB", "imf_dev")
POSTGRES_USER     = os.environ.get("POSTGRES_USER", "imf")
POSTGRES_PASSWORD = os.environ.get("POSTGRES_PASSWORD", "imf_pass")
JDBC_URL          = f"jdbc:postgresql://{POSTGRES_HOST}:{POSTGRES_PORT}/{POSTGRES_DB}"

SPARK_MASTER      = os.environ.get("SPARK_MASTER_URL", "spark://spark-master:7077")
EXECUTION_DATE    = os.environ.get("EXECUTION_DATE", str(date.today()))  # injected by Airflow

# Profils régionaux camerounais (miroir de REGION_PROFILES dans generate_warehouse.py)
REGION_PROFILES = {
    "REG01": {"risque_base": 1.15, "penetration_mobile": 0.45, "zone_agro": 2},  # Adamaoua
    "REG02": {"risque_base": 1.00, "penetration_mobile": 0.75, "zone_agro": 1},  # Centre
    "REG03": {"risque_base": 1.20, "penetration_mobile": 0.30, "zone_agro": 1},  # Est
    "REG04": {"risque_base": 1.45, "penetration_mobile": 0.25, "zone_agro": 0},  # Extrême-Nord
    "REG05": {"risque_base": 0.90, "penetration_mobile": 0.85, "zone_agro": 3},  # Littoral
    "REG06": {"risque_base": 1.30, "penetration_mobile": 0.35, "zone_agro": 0},  # Nord
    "REG07": {"risque_base": 1.25, "penetration_mobile": 0.55, "zone_agro": 2},  # Nord-Ouest
    "REG08": {"risque_base": 1.00, "penetration_mobile": 0.65, "zone_agro": 2},  # Ouest
    "REG09": {"risque_base": 1.10, "penetration_mobile": 0.35, "zone_agro": 1},  # Sud
    "REG10": {"risque_base": 1.20, "penetration_mobile": 0.50, "zone_agro": 3},  # Sud-Ouest
}

# Calendrier agricole : mois actifs par région pour saison_recolte_active
MOIS_RECOLTE = {
    "REG01": [7, 8],
    "REG02": [3, 4, 5, 10, 11, 12],
    "REG03": [10, 11, 12],
    "REG04": [9, 10, 11],
    "REG05": list(range(1, 13)),
    "REG06": [9, 10, 11],
    "REG07": [11, 12, 1, 2],
    "REG08": [7, 8, 11, 12, 1],
    "REG09": [10, 11, 12],
    "REG10": [1, 2, 3, 4, 5, 9, 10, 11, 12],
}


def build_region_profiles_expr(spark):
    """Crée un DataFrame de référence des profils régionaux."""
    from pyspark.sql import Row
    mois_actuel = date.today().month
    rows = [
        Row(
            region_id=reg,
            risque_regional=float(p["risque_base"]),
            taux_penetration_mobile=float(p["penetration_mobile"]),
            zone_agroclimatique=float(p["zone_agro"]),
            saison_recolte_active=float(1 if mois_actuel in MOIS_RECOLTE[reg] else 0),
        )
        for reg, p in REGION_PROFILES.items()
    ]
    return spark.createDataFrame(rows)


def jdbc_read(spark, query: str, num_partitions: int = 8):
    """Lit une table/vue PostgreSQL via JDBC avec parallélisme."""
    return (
        spark.read
        .format("jdbc")
        .option("url", JDBC_URL)
        .option("dbtable", f"({query}) AS q")
        .option("user", POSTGRES_USER)
        .option("password", POSTGRES_PASSWORD)
        .option("driver", "org.postgresql.Driver")
        .option("numPartitions", num_partitions)
        .load()
    )


def jdbc_write(df, table: str, mode: str = "overwrite"):
    """Écrit un DataFrame dans PostgreSQL via JDBC."""
    (
        df.write
        .format("jdbc")
        .option("url", JDBC_URL)
        .option("dbtable", table)
        .option("user", POSTGRES_USER)
        .option("password", POSTGRES_PASSWORD)
        .option("driver", "org.postgresql.Driver")
        .mode(mode)
        .save()
    )


def compute_crs_features(collectes_df, credits_df):
    """
    Collection Reliability Score features (7 variables).
    Basé sur l'historique des collectes terrain et des prêts.
    """
    from pyspark.sql import functions as F

    # Agrégations sur les collectes
    crs = collectes_df.groupBy("client_id_externe", "imf_id").agg(
        F.count("*").alias("nb_collectes_total"),
        F.sum(F.when(F.col("statut") == "CONFIRMEE", 1).otherwise(0)).alias("nb_confirmees"),
        F.sum("montant").alias("montant_total_collecte"),
        F.countDistinct("agent_id").alias("nb_agents_distincts"),
        F.max("date_collecte").alias("derniere_collecte"),
    )

    crs = crs.withColumn(
        "regularite",
        F.when(F.col("nb_collectes_total") > 0,
               F.col("nb_confirmees") / F.col("nb_collectes_total")).otherwise(0.0)
    ).withColumn(
        "nb_jours_depuis_derniere_collecte",
        F.datediff(F.current_date(), F.col("derniere_collecte"))
    )

    # Agrégations sur les prêts
    pret_agg = credits_df.groupBy("client_id_externe", "imf_id").agg(
        F.sum("montant_encours").alias("encours_total"),
        F.avg("taux_remboursement").alias("taux_remboursement"),
        F.count("*").alias("nb_prets"),
    )

    return crs.join(pret_agg, on=["client_id_externe", "imf_id"], how="left")


def compute_rps_features(creances_df, historique_df):
    """
    Recovery Prediction Score features (6 variables).
    Basé sur les créances et l'historique de paiement.
    """
    from pyspark.sql import functions as F

    rps = creances_df.groupBy("client_id_externe", "imf_id").agg(
        F.max("jours_retard").alias("jours_retard_actuel"),
        F.sum("montant_encours").alias("encours_creance"),
        F.count("*").alias("nb_creances_actives"),
        F.sum(F.when(F.col("cobac_classe").isin("D", "E"), 1).otherwise(0))
         .alias("nb_creances_critiques"),
    )

    hist_agg = historique_df.groupBy("client_id_externe", "imf_id").agg(
        F.sum("montant_paiement").alias("montant_rembourse_historique"),
        F.count(F.when(F.col("statut_paiement") == "EN_RETARD", True)).alias("nb_incidents"),
        F.sum("montant_initial").alias("montant_initial_total"),
    ).withColumn(
        "taux_remboursement_historique",
        F.when(F.col("montant_initial_total") > 0,
               F.col("montant_rembourse_historique") / F.col("montant_initial_total")
               ).otherwise(1.0)
    )

    return rps.join(hist_agg, on=["client_id_externe", "imf_id"], how="left")


def compute_csi_features(clients_df, marche_df):
    """
    Client Solvency Index features (13 variables).
    Basé sur le profil client et les données macro-économiques régionales.
    """
    from pyspark.sql import functions as F
    import math

    csi = clients_df.select(
        "client_id_externe", "imf_id",
        "revenu_mensuel_estime",
        "nombre_personnes_charge",
        "annees_experience",
        "secteur_principal",
        F.coalesce(F.col("zone_id"), F.lit("REG02")).alias("region_id"),
    ).withColumn(
        "ratio_charge_revenu",
        F.when(F.col("revenu_mensuel_estime") > 0,
               F.col("nombre_personnes_charge") / F.col("revenu_mensuel_estime")
               ).otherwise(0.5)
    )

    # Données macro-économiques (inflation, précipitations) depuis la table de référence
    macro = marche_df.select(
        "region_id",
        F.avg("inflation_annuelle").alias("inflation"),
        F.avg("precipitations_mm").alias("precipitations"),
        F.avg("indice_secheresse").alias("indice_secheresse"),
        F.avg("taux_chomage").alias("taux_chomage"),
    )

    return csi.join(macro, on="region_id", how="left")


def main():
    from pyspark.sql import SparkSession
    from pyspark.sql import functions as F

    spark = (
        SparkSession.builder
        .appName("imf-feature-engineering")
        .master(SPARK_MASTER)
        .config("spark.sql.shuffle.partitions", "16")
        .config("spark.default.parallelism", "16")
        .config("spark.sql.adaptive.enabled", "true")
        .getOrCreate()
    )
    spark.sparkContext.setLogLevel("WARN")
    log.info("Spark session demarree — execution_date=%s", EXECUTION_DATE)

    # ── Lecture des sources PostgreSQL ────────────────────────────────────────
    collectes = jdbc_read(spark, """
        SELECT client_id_externe, imf_id, agent_id, statut, montant, date_collecte
        FROM app.collectes_epargne
        WHERE date_collecte >= CURRENT_DATE - INTERVAL '12 months'
    """)

    creances = jdbc_read(spark, """
        SELECT c.client_id_externe, c.imf_id, cr.montant_encours,
               cr.jours_retard, cr.cobac_classe
        FROM app.creances cr
        JOIN app.clients_informels c ON c.id = cr.client_id
        WHERE cr.statut_pret NOT IN ('REMBOURSE', 'ANNULE')
    """)

    historique = jdbc_read(spark, """
        SELECT c.client_id_externe, c.imf_id,
               e.montant_initial_echeance AS montant_initial,
               e.montant_verse AS montant_paiement,
               e.statut AS statut_paiement
        FROM app.echeances_app e
        JOIN app.creances cr ON cr.id = e.creance_id
        JOIN app.clients_informels c ON c.id = cr.client_id
    """)

    clients = jdbc_read(spark, """
        SELECT c.client_id_externe, c.imf_id, c.revenu_mensuel_estime,
               c.nombre_personnes_charge, c.annees_experience,
               c.secteur_principal, c.zone_id
        FROM app.clients_informels c
        WHERE c.actif = true
    """)

    credits = jdbc_read(spark, """
        SELECT c.client_id_externe, c.imf_id, cr.montant_encours,
               CASE WHEN cr.montant_initial > 0
                    THEN cr.montant_rembourse::float / cr.montant_initial::float
                    ELSE 1.0 END AS taux_remboursement
        FROM app.creances cr
        JOIN app.clients_informels c ON c.id = cr.client_id
    """)

    macro = jdbc_read(spark, """
        SELECT region_id,
               AVG(inflation_annuelle)  AS inflation_annuelle,
               AVG(precipitations_mm)   AS precipitations_mm,
               AVG(indice_secheresse)   AS indice_secheresse,
               AVG(taux_chomage)        AS taux_chomage
        FROM dw.macro_economique_cameroun
        WHERE annee = EXTRACT(YEAR FROM CURRENT_DATE)
        GROUP BY region_id
    """)

    # ── Calcul des features ───────────────────────────────────────────────────
    crs_df = compute_crs_features(collectes, credits)
    rps_df = compute_rps_features(creances, historique)
    csi_df = compute_csi_features(clients, macro)

    # Profils régionaux camerounais (locaux — pas de requête DB)
    region_df = build_region_profiles_expr(spark)

    # ── Jointure finale ───────────────────────────────────────────────────────
    features = (
        csi_df
        .join(crs_df, on=["client_id_externe", "imf_id"], how="left")
        .join(rps_df, on=["client_id_externe", "imf_id"], how="left")
        .join(region_df, on="region_id", how="left")
        .withColumn("ratio_creance_revenus",
            F.when((F.col("revenu_mensuel_estime").isNotNull()) &
                   (F.col("revenu_mensuel_estime") > 0),
                   F.col("encours_creance") / F.col("revenu_mensuel_estime") * 12
                   ).otherwise(0.0)
        )
        .fillna({
            "regularite": 0.0,
            "taux_remboursement": 1.0,
            "jours_retard_actuel": 0,
            "nb_incidents": 0,
            "encours_creance": 0.0,
            "inflation": 3.5,
            "precipitations": 100.0,
            "indice_secheresse": 0.5,
            "taux_chomage": 7.0,
            "risque_regional": 1.12,
            "taux_penetration_mobile": 0.50,
            "zone_agroclimatique": 1.0,
            "saison_recolte_active": 0.0,
        })
        .withColumn("computed_at", F.current_timestamp())
        .withColumn("execution_date", F.lit(EXECUTION_DATE))
    )

    n = features.count()
    log.info("Features calculees pour %d clients", n)

    # ── Écriture dans ml.features_client ─────────────────────────────────────
    jdbc_write(features, "ml.features_client", mode="overwrite")
    log.info("Ecriture terminee dans ml.features_client")

    spark.stop()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s %(levelname)s %(name)s — %(message)s")
    main()
