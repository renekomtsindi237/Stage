"""
IMF Pipeline — Flink KPI Temps-Réel Job
========================================

Job PyFlink soumis sur le cluster Flink standalone.
Consomme le topic imf.collectes.confirmed et calcule les KPI agents
sur une fenêtre glissante de 15 minutes, puis publie sur imf.kpi.agents.realtime.

KPI calculés par fenêtre (par agent, par agence, par région) :
  - montant_collecte_total        : somme FCFA dans la fenêtre
  - nb_collectes                  : nombre d'événements confirmés
  - montant_moyen_collecte        : moyenne FCFA
  - taux_mobile_money             : ratio Mobile Money / total
  - nb_clients_distincts          : cardinalité des clients touchés
  - vitesse_collecte_heure        : extrapolation horaire de la cadence

Ces KPI sont consommés par le backend Spring Boot via SseEventListener
pour l'affichage temps-réel sur le tableau de bord superviseur.

Usage (soumis par flink-job-submitter dans docker-compose.analytics.yml) :
  flink run -py /opt/flink-jobs/kpi_realtime_job.py
"""
from __future__ import annotations

import json
import logging
import os

log = logging.getLogger("imf.flink.kpi_realtime")

# ─── Paramètres d'environnement ───────────────────────────────────────────────

KAFKA_BOOTSTRAP  = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
SCHEMA_REGISTRY  = os.environ.get("SCHEMA_REGISTRY_URL", "http://schema-registry:8081")
FLINK_CHECKPOINT = os.environ.get("FLINK_CHECKPOINT_DIR",
                                   "file:///opt/flink/checkpoints/kpi_realtime")
WINDOW_MINUTES   = int(os.environ.get("KPI_WINDOW_MINUTES", "15"))
SLIDE_MINUTES    = int(os.environ.get("KPI_SLIDE_MINUTES", "5"))

TOPIC_IN  = "imf.collectes.confirmed"
TOPIC_OUT = "imf.kpi.agents.realtime"

CANAL_MOBILE = {"MOBILE_MONEY_MTN", "MOBILE_MONEY_ORANGE"}


def build_pipeline():
    """Construit et retourne le pipeline Flink complet."""
    from pyflink.datastream import StreamExecutionEnvironment, TimeCharacteristic
    from pyflink.datastream.connectors.kafka import (
        KafkaSource, KafkaSink, KafkaRecordSerializationSchema,
        DeliveryGuarantee,
    )
    from pyflink.datastream.window import SlidingEventTimeWindows, Time
    from pyflink.common import WatermarkStrategy, Duration, SimpleStringSchema
    from pyflink.datastream.functions import (
        ProcessWindowFunction, KeyedProcessFunction,
    )
    from pyflink.common.typeinfo import Types

    env = StreamExecutionEnvironment.get_execution_environment()
    env.set_stream_time_characteristic(TimeCharacteristic.EventTime)
    env.set_parallelism(2)
    env.enable_checkpointing(60_000)  # checkpoint toutes les 60s
    env.get_checkpoint_config().set_checkpoint_storage_dir(FLINK_CHECKPOINT)

    # ── Source Kafka ──────────────────────────────────────────────────────────
    source = (
        KafkaSource.builder()
        .set_bootstrap_servers(KAFKA_BOOTSTRAP)
        .set_topics(TOPIC_IN)
        .set_group_id("imf-flink-kpi-realtime")
        .set_starting_offsets_from_earliest()
        .set_value_only_deserializer(SimpleStringSchema())
        .build()
    )

    # Watermark : tolérance de 30s pour les événements hors-ordre (réseau mobile)
    watermark_strategy = (
        WatermarkStrategy
        .for_bounded_out_of_orderness(Duration.of_seconds(30))
        .with_timestamp_assigner(_CollecteTimestampAssigner())
    )

    stream = env.from_source(source, watermark_strategy, "kafka-collectes-confirmed")

    # ── Parsing JSON → dict ───────────────────────────────────────────────────
    parsed = stream.map(
        lambda raw: json.loads(raw),
        output_type=Types.MAP(Types.STRING(), Types.STRING())
    )

    # ── Fenêtre glissante 15min / slide 5min par agent ────────────────────────
    kpi_stream = (
        parsed
        .key_by(lambda e: f"{e.get('agent_id','?')}|{e.get('agence_id','?')}|{e.get('region_id','?')}")
        .window(SlidingEventTimeWindows.of(
            Time.minutes(WINDOW_MINUTES),
            Time.minutes(SLIDE_MINUTES)
        ))
        .process(_KpiWindowFunction(), output_type=Types.STRING())
    )

    # ── Sink Kafka ────────────────────────────────────────────────────────────
    sink = (
        KafkaSink.builder()
        .set_bootstrap_servers(KAFKA_BOOTSTRAP)
        .set_record_serializer(
            KafkaRecordSerializationSchema.builder()
            .set_topic(TOPIC_OUT)
            .set_value_serialization_schema(SimpleStringSchema())
            .build()
        )
        .set_delivery_guarantee(DeliveryGuarantee.AT_LEAST_ONCE)
        .build()
    )

    kpi_stream.sink_to(sink)
    return env


class _CollecteTimestampAssigner:
    """Extrait le timestamp_ms du message JSON pour le watermark Flink."""

    def extract_timestamp(self, element: dict, record_timestamp: int) -> int:
        try:
            return int(element.get("timestamp_ms", record_timestamp))
        except (TypeError, ValueError):
            return record_timestamp


class _KpiWindowFunction(ProcessWindowFunction):
    """
    Calcule les KPI agrégés sur la fenêtre glissante.
    Produit un objet JSON par (agent_id, agence_id, region_id, fenêtre).
    """

    def process(self, key: str, context, elements) -> list[str]:
        import time

        agent_id, agence_id, region_id = key.split("|")

        montant_total   = 0.0
        nb_collectes    = 0
        nb_mobile       = 0
        clients         = set()

        for e in elements:
            try:
                montant = float(e.get("montant", 0))
                canal   = str(e.get("canal", ""))
                client  = str(e.get("client_id_externe", ""))
            except (TypeError, ValueError):
                continue

            montant_total += montant
            nb_collectes  += 1
            clients.add(client)
            if canal in CANAL_MOBILE:
                nb_mobile += 1

        if nb_collectes == 0:
            return []

        window_start_ms = context.window().start
        window_end_ms   = context.window().end
        window_duration_h = WINDOW_MINUTES / 60.0

        kpi = {
            "agent_id":                  agent_id,
            "agence_id":                 agence_id,
            "region_id":                 region_id,
            "window_start_ms":           window_start_ms,
            "window_end_ms":             window_end_ms,
            "window_minutes":            WINDOW_MINUTES,
            "montant_collecte_total":    round(montant_total, 2),
            "nb_collectes":              nb_collectes,
            "montant_moyen_collecte":    round(montant_total / nb_collectes, 2),
            "taux_mobile_money":         round(nb_mobile / nb_collectes, 4),
            "nb_clients_distincts":      len(clients),
            "vitesse_collecte_heure":    round(nb_collectes / window_duration_h, 2),
            "timestamp_ms":              int(time.time() * 1000),
        }
        return [json.dumps(kpi, ensure_ascii=False)]


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s — %(message)s"
    )
    log.info("Demarrage du job Flink KPI temps-reel")
    log.info("Source=%s  Sink=%s  Window=%dmin/slide=%dmin",
             TOPIC_IN, TOPIC_OUT, WINDOW_MINUTES, SLIDE_MINUTES)

    env = build_pipeline()
    env.execute("imf-kpi-agents-realtime")


if __name__ == "__main__":
    main()
