"""
DAG : dag_donnees_externes
Fréquence : quotidien à 04h00

Collecte tous les facteurs externes nécessaires aux features ML MCRS :
  1. fetch_prix_marche_agents  — collectes prix terrain soumises via l'app
  2. fetch_mincommerce         — scraping/API MINCOMMERCE Cameroun
  3. fetch_beac_indicateurs    — indicateurs BEAC (taux directeur, inflation)
  4. fetch_ins_cameroun        — données INS (IPC, emploi)
  5. fetch_meteo_open_meteo    — météo zones IMF via Open-Meteo API
  6. fetch_evenements          — calendrier fêtes, marchés, élections
  7. mapper_produits           — mapping codes sources → app.produits_generiques
  8. dbt_stg_externes          — dbt run staging.stg_prix_produits + stg_indicateurs_macro + stg_meteo
  9. dbt_int_externes          — moyennes mobiles, volatilité, tendances
 10. maj_app_tables            — upsert app.prix_produits + app.facteurs_macro + app.donnees_meteo
 11. log_journal
"""

from __future__ import annotations

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.empty import EmptyOperator
from airflow.operators.python import PythonOperator
from airflow.utils.trigger_rule import TriggerRule

from scripts.dbt_utils import dbt_run_select
from scripts.donnees_externes_utils import (
    fetch_evenements_calendrier,
    fetch_indicateurs_beac,
    fetch_indicateurs_ins_cameroun,
    fetch_meteo_open_meteo,
    fetch_prix_marche_agents_terrain,
    fetch_prix_mincommerce,
    maj_app_donnees_meteo,
    maj_app_facteurs_macro,
    maj_app_prix_produits,
    mapper_produits_generiques,
)
from scripts.ingestion_utils import log_journal

DEFAULT_ARGS = {
    "owner": "pipeline-imf",
    "retries": 3,
    "retry_delay": timedelta(minutes=15),
    "email_on_failure": False,
}

with DAG(
    dag_id="dag_donnees_externes",
    description="Collecte quotidienne données externes — prix produits, macro, météo, événements",
    schedule_interval="0 4 * * *",  # 04h00 avant le scoring ML
    start_date=datetime(2025, 1, 1),
    catchup=False,
    max_active_runs=1,
    tags=["externe", "prix", "macro", "meteo", "features"],
    default_args=DEFAULT_ARGS,
    doc_md=__doc__,
) as dag:

    debut = EmptyOperator(task_id="debut")
    fin = EmptyOperator(task_id="fin", trigger_rule=TriggerRule.ALL_DONE)

    # ── Fetch parallèle des sources ─────────────────────────────────────────
    prix_terrain = PythonOperator(
        task_id="fetch_prix_marche_agents",
        python_callable=fetch_prix_marche_agents_terrain,
        doc="Prix marché collectés par agents via l'app mobile (endpoint /api/prix-marche)",
    )

    prix_mincom = PythonOperator(
        task_id="fetch_mincommerce",
        python_callable=fetch_prix_mincommerce,
        op_kwargs={
            "url_base": "https://www.mincommerce.cm",
            "produits_suivis": [
                "MAIS",
                "MANIOC",
                "PLANTAIN",
                "ARACHIDE",
                "TOMATE",
                "HUILE_P",
                "POISSON_S",
                "POULET",
                "CACAO",
                "CAFE_R",
            ],
        },
        doc="Scraping prix officiels MINCOMMERCE Cameroun",
    )

    beac = PythonOperator(
        task_id="fetch_beac_indicateurs",
        python_callable=fetch_indicateurs_beac,
        op_kwargs={
            "indicateurs": [
                "TAUX_DIRECTEUR_BEAC",
                "TAUX_INFLATION_MENSUEL",
                "COURS_EUR_XAF",
                "COURS_USD_XAF",
            ]
        },
    )

    ins = PythonOperator(
        task_id="fetch_ins_cameroun",
        python_callable=fetch_indicateurs_ins_cameroun,
        op_kwargs={
            "indicateurs": [
                "INDICE_PRIX_CONSOMMATION",
                "TAUX_CHOMAGE",
                "INDICE_PRODUCTION_AGRICOLE",
            ]
        },
    )

    meteo = PythonOperator(
        task_id="fetch_meteo_open_meteo",
        python_callable=fetch_meteo_open_meteo,
        op_kwargs={
            "zones": {
                "YAOUNDE": {"lat": 3.848, "lon": 11.502},
                "DOUALA": {"lat": 4.050, "lon": 9.700},
                "GAROUA": {"lat": 9.301, "lon": 13.398},
                "BAFOUSSAM": {"lat": 5.479, "lon": 10.418},
                "BERTOUA": {"lat": 4.578, "lon": 13.685},
                "MAROUA": {"lat": 10.591, "lon": 14.317},
                "EBOLOWA": {"lat": 2.900, "lon": 11.150},
                "BAMENDA": {"lat": 5.961, "lon": 10.146},
                "NGAOUNDERE": {"lat": 7.328, "lon": 13.584},
                "BUEA": {"lat": 4.154, "lon": 9.243},
            },
            "variables": [
                "temperature_2m_min",
                "temperature_2m_max",
                "precipitation_sum",
                "relative_humidity_2m_mean",
            ],
        },
    )

    evenements = PythonOperator(
        task_id="fetch_evenements",
        python_callable=fetch_evenements_calendrier,
        op_kwargs={"horizon_jours": 30},
        doc="Calendrier fêtes nationales, marchés périodiques, élections prévues",
    )

    # ── Mapping et normalisation ────────────────────────────────────────────
    mapping = PythonOperator(
        task_id="mapper_produits",
        python_callable=mapper_produits_generiques,
        doc="Mappe codes sources (MINCOMMERCE, terrain) → app.produits_generiques",
    )

    # ── dbt transformations ─────────────────────────────────────────────────
    dbt_stg = PythonOperator(
        task_id="dbt_stg_externes",
        python_callable=dbt_run_select,
        op_kwargs={
            "select": (
                "staging.stg_prix_produits "
                "staging.stg_indicateurs_macro "
                "staging.stg_meteo"
            )
        },
    )

    dbt_int = PythonOperator(
        task_id="dbt_int_externes",
        python_callable=dbt_run_select,
        op_kwargs={
            "select": (
                "intermediate.int_prix_produits_moyennes "
                "intermediate.int_macro_tendances "
                "intermediate.int_meteo_indices"
            )
        },
    )

    # ── MAJ tables app.* ────────────────────────────────────────────────────
    maj_prix = PythonOperator(
        task_id="maj_app_prix_produits",
        python_callable=maj_app_prix_produits,
    )

    maj_macro = PythonOperator(
        task_id="maj_app_facteurs_macro",
        python_callable=maj_app_facteurs_macro,
    )

    maj_meteo = PythonOperator(
        task_id="maj_app_donnees_meteo",
        python_callable=maj_app_donnees_meteo,
    )

    journal = PythonOperator(
        task_id="log_journal",
        python_callable=log_journal,
        op_kwargs={
            "dag_id": "dag_donnees_externes",
            "table_cible": "staging.stg_prix_produits",
        },
        trigger_rule=TriggerRule.ALL_DONE,
    )

    # Fetch en parallèle
    debut >> [prix_terrain, prix_mincom, beac, ins, meteo, evenements]
    [prix_terrain, prix_mincom] >> mapping
    [mapping, beac, ins, meteo, evenements] >> dbt_stg >> dbt_int
    dbt_int >> [maj_prix, maj_macro, maj_meteo]
    [maj_prix, maj_macro, maj_meteo] >> journal >> fin
