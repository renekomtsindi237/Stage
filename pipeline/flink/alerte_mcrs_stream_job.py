"""
IMF Pipeline — Flink Alerte MCRS Stream Job
============================================

Job PyFlink qui consomme les résultats de scoring (imf.ml.scoring.results)
et détecte en temps-réel les franchissements de seuils MCRS régionaux.
Publie les alertes sur imf.alertes.risque.

Seuils de déclenchement (adapté au contexte camerounais) :
  MODERE   : score_mcrs >= 0.40 (pré-alerte, surveillance renforcée)
  ELEVE    : score_mcrs >= 0.65 (intervention préventive)
  CRITIQUE : score_mcrs >= 0.80 (mise en demeure immédiate)

Logique de déduplication :
  - Une alerte de même niveau ne se répète pas avant 24h pour un même client
  - État maintenu via RocksDB state backend (checkpoint Flink)
"""
from __future__ import annotations

import json
import logging
import os
import time as _time

log = logging.getLogger("imf.flink.alerte_mcrs")

KAFKA_BOOTSTRAP  = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
FLINK_CHECKPOINT = os.environ.get("FLINK_CHECKPOINT_DIR",
                                   "file:///opt/flink/checkpoints/alerte_mcrs")

TOPIC_IN  = "imf.ml.scoring.results"
TOPIC_OUT = "imf.alertes.risque"

# Seuils MCRS par niveau (calibrés sur le portefeuille camerounais)
SEUIL_MODERE   = 0.40
SEUIL_ELEVE    = 0.65
SEUIL_CRITIQUE = 0.80

# Cooldown entre deux alertes identiques (ms)
COOLDOWN_MS = 24 * 3600 * 1000  # 24h


def _niveau_risque(score_mcrs: float) -> str | None:
    if score_mcrs >= SEUIL_CRITIQUE:
        return "CRITIQUE"
    if score_mcrs >= SEUIL_ELEVE:
        return "ELEVE"
    if score_mcrs >= SEUIL_MODERE:
        return "MODERE"
    return None


def _action_recouvrement(niveau: str, jours_retard: int) -> str:
    if niveau == "CRITIQUE" or jours_retard >= 90:
        return "MISE_EN_DEMEURE"
    if niveau == "ELEVE" or jours_retard >= 30:
        return "VISITE_TERRAIN"
    return "RELANCE_PREVENTIVE"


def _cobac_classe(jours_retard: int) -> str:
    if jours_retard < 30:  return "A"
    if jours_retard < 90:  return "B"
    if jours_retard < 180: return "C"
    if jours_retard < 360: return "D"
    return "E"


def _cobac_provision(classe: str) -> float:
    return {"A": 0.00, "B": 0.20, "C": 0.50, "D": 0.80, "E": 1.00}[classe]


def _types_alertes(score_mcrs: float, jours_retard: int,
                   prev_mcrs: float | None) -> list[str]:
    alertes = []
    if score_mcrs >= SEUIL_CRITIQUE:
        alertes.append("RISQUE_DEFAUT_IMMINENT")
    if prev_mcrs is not None and score_mcrs - prev_mcrs >= 0.15:
        alertes.append("DETERIORATION_RAPIDE")
    if jours_retard >= 30:
        alertes.append("RETARD_PAIEMENT_SIGNIFICATIF")
    return alertes or ["SCORE_MCRS_ELEVE"]


def build_pipeline():
    from pyflink.datastream import StreamExecutionEnvironment, TimeCharacteristic
    from pyflink.datastream.connectors.kafka import (
        KafkaSource, KafkaSink, KafkaRecordSerializationSchema,
        DeliveryGuarantee,
    )
    from pyflink.common import WatermarkStrategy, Duration, SimpleStringSchema
    from pyflink.datastream.functions import KeyedProcessFunction
    from pyflink.common.typeinfo import Types
    from pyflink.datastream.state import ValueStateDescriptor

    env = StreamExecutionEnvironment.get_execution_environment()
    env.set_stream_time_characteristic(TimeCharacteristic.EventTime)
    env.set_parallelism(2)
    env.enable_checkpointing(60_000)
    env.get_checkpoint_config().set_checkpoint_storage_dir(FLINK_CHECKPOINT)

    source = (
        KafkaSource.builder()
        .set_bootstrap_servers(KAFKA_BOOTSTRAP)
        .set_topics(TOPIC_IN)
        .set_group_id("imf-flink-alerte-mcrs")
        .set_starting_offsets_from_earliest()
        .set_value_only_deserializer(SimpleStringSchema())
        .build()
    )

    watermark_strategy = (
        WatermarkStrategy
        .for_bounded_out_of_orderness(Duration.of_seconds(30))
        .with_timestamp_assigner(_TimestampAssigner())
    )

    stream = env.from_source(source, watermark_strategy, "kafka-scoring-results")

    parsed = stream.map(
        lambda raw: json.loads(raw),
        output_type=Types.MAP(Types.STRING(), Types.STRING())
    )

    alerte_stream = (
        parsed
        .key_by(lambda e: f"{e.get('client_id_externe','?')}|{e.get('imf_id','0')}")
        .process(_AlerteDetectionFunction(), output_type=Types.STRING())
        .filter(lambda x: x is not None and x != "")
    )

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

    alerte_stream.sink_to(sink)
    return env


class _TimestampAssigner:
    def extract_timestamp(self, element: dict, record_ts: int) -> int:
        try:
            return int(element.get("timestamp_ms", record_ts))
        except (TypeError, ValueError):
            return record_ts


class _AlerteDetectionFunction:
    """
    Détecte les franchissements de seuils MCRS avec état Flink (RocksDB).
    État par client : (derniere_alerte_ms, niveau_precedent, score_precedent).
    """

    def open(self, runtime_context):
        from pyflink.datastream.state import ValueStateDescriptor
        from pyflink.common.typeinfo import Types

        self._last_alerte_ms = runtime_context.get_state(
            ValueStateDescriptor("last_alerte_ms", Types.LONG())
        )
        self._last_niveau = runtime_context.get_state(
            ValueStateDescriptor("last_niveau", Types.STRING())
        )
        self._last_score = runtime_context.get_state(
            ValueStateDescriptor("last_score", Types.DOUBLE())
        )

    def process_element(self, element: dict, ctx) -> str:
        import uuid

        try:
            score_mcrs   = float(element.get("score_mcrs", 0))
            score_crs    = float(element.get("score_crs", 0))
            score_rps    = float(element.get("score_rps", 0))
            score_csi    = float(element.get("score_csi", 0))
            client_id    = str(element.get("client_id_externe", ""))
            imf_id       = int(element.get("imf_id", 0))
            agence_id    = str(element.get("agence_id", ""))
            region_id    = str(element.get("region_id", "REG02"))
            jours_retard = int(element.get("jours_retard", 0))
            now_ms       = int(element.get("timestamp_ms", _time.time() * 1000))
        except (TypeError, ValueError, AttributeError):
            return ""

        niveau = _niveau_risque(score_mcrs)
        if niveau is None:
            return ""

        # Déduplication : même niveau + cooldown 24h
        last_ms    = self._last_alerte_ms.value() or 0
        last_niveau = self._last_niveau.value() or ""
        prev_score  = self._last_score.value()

        if niveau == last_niveau and (now_ms - last_ms) < COOLDOWN_MS:
            return ""

        # Mise à jour de l'état
        self._last_alerte_ms.update(now_ms)
        self._last_niveau.update(niveau)
        self._last_score.update(score_mcrs)

        cobac = _cobac_classe(jours_retard)
        alerte = {
            "event_id":              str(uuid.uuid4()),
            "alerte_id":             str(uuid.uuid4()),
            "client_id_externe":     client_id,
            "imf_id":                imf_id,
            "agence_id":             agence_id,
            "region_id":             region_id,
            "score_mcrs":            score_mcrs,
            "score_crs":             score_crs,
            "score_rps":             score_rps,
            "score_csi":             score_csi,
            "niveau_risque":         niveau,
            "cobac_classe":          cobac,
            "cobac_provision_taux":  _cobac_provision(cobac),
            "types_alertes":         _types_alertes(score_mcrs, jours_retard, prev_score),
            "jours_retard":          jours_retard,
            "action_recommandee":    _action_recouvrement(niveau, jours_retard),
            "timestamp_ms":          now_ms,
            "source":                "stream_flink",
        }
        return json.dumps(alerte, ensure_ascii=False)


def main():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s — %(message)s"
    )
    log.info("Demarrage du job Flink Alerte MCRS Stream")
    env = build_pipeline()
    env.execute("imf-alerte-mcrs-stream")


if __name__ == "__main__":
    main()
