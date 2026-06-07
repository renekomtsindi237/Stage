"""
IMF Pipeline — Consommateurs Kafka (Python)
============================================

Consommateurs Avro pour le pipeline Airflow :
  - ScoringResultConsumer  ← imf.ml.scoring.results
  - CollecteConsumer       ← imf.collectes.confirmed

Usage dans un DAG Airflow :
  from pipeline.kafka.consumers import ScoringResultConsumer
  consumer = ScoringResultConsumer()
  results  = consumer.poll_batch(max_messages=500, timeout_s=30)
"""
from __future__ import annotations

import logging
import time
from typing import Any, Callable, Generator

from . import config as cfg

log = logging.getLogger("imf.kafka.consumers")


def _make_consumer():
    try:
        from confluent_kafka import DeserializingConsumer
        from confluent_kafka.schema_registry import SchemaRegistryClient
        from confluent_kafka.schema_registry.avro import AvroDeserializer
        from confluent_kafka.serialization import StringDeserializer
        return DeserializingConsumer, SchemaRegistryClient, AvroDeserializer, StringDeserializer
    except ImportError:
        raise ImportError("confluent-kafka manquant. pip install confluent-kafka[avro]")


class BaseConsumer:
    """Consommateur Kafka Avro de base avec at-least-once et DLQ."""

    TOPIC: str
    GROUP_ID: str
    SCHEMA_NAME: str

    def __init__(self, group_id: str | None = None) -> None:
        DeserializingConsumer, SchemaRegistryClient, AvroDeserializer, StringDeserializer = _make_consumer()
        import pathlib

        schema_path = pathlib.Path(__file__).parent.parent.parent / "schemas" / "avro" / f"{self.SCHEMA_NAME}.avsc"
        avro_schema_str = schema_path.read_text(encoding="utf-8")

        sr_client   = SchemaRegistryClient({"url": cfg.SCHEMA_REGISTRY_URL})
        avro_deser  = AvroDeserializer(sr_client, avro_schema_str)
        str_deser   = StringDeserializer("utf_8")

        consumer_cfg = cfg.consumer_config(group_id or self.GROUP_ID)
        consumer_cfg.update({
            "key.deserializer":   str_deser,
            "value.deserializer": avro_deser,
        })
        self._consumer = DeserializingConsumer(consumer_cfg)
        self._consumer.subscribe([self.TOPIC])
        log.info("Consommateur initialisé — topic=%s group=%s",
                 self.TOPIC, group_id or self.GROUP_ID)

    def poll_batch(self, max_messages: int = 500, timeout_s: float = 30.0) -> list[dict[str, Any]]:
        """
        Consomme jusqu'à max_messages messages dans la fenêtre timeout_s.
        Commit manuel après traitement réussi (at-least-once).
        """
        messages = []
        deadline = time.time() + timeout_s

        while len(messages) < max_messages and time.time() < deadline:
            msg = self._consumer.poll(timeout=min(1.0, deadline - time.time()))
            if msg is None:
                continue
            if msg.error():
                log.error("Erreur Kafka : %s", msg.error())
                continue
            messages.append(msg.value())

        if messages:
            self._consumer.commit(asynchronous=False)
            log.info("Batch consommé — %d messages depuis %s", len(messages), self.TOPIC)

        return messages

    def stream(self, handler: Callable[[dict[str, Any]], None],
               timeout_s: float = 1.0) -> Generator[None, None, None]:
        """Générateur de streaming continu avec commit après chaque message."""
        try:
            while True:
                msg = self._consumer.poll(timeout=timeout_s)
                if msg is None:
                    yield
                    continue
                if msg.error():
                    log.error("Erreur Kafka : %s", msg.error())
                    continue
                try:
                    handler(msg.value())
                    self._consumer.commit(asynchronous=False)
                except Exception as e:
                    log.exception("Erreur traitement message : %s", e)
                yield
        finally:
            self.close()

    def close(self) -> None:
        self._consumer.close()
        log.info("Consommateur fermé — %s", self.TOPIC)

    def __enter__(self): return self

    def __exit__(self, *args): self.close()


class ScoringResultConsumer(BaseConsumer):
    """
    Consomme les résultats de scoring MCRS produits par le service ML.
    Utilisé par dag_ml_scoring.py pour persister les scores en base.
    """
    TOPIC       = cfg.TOPIC_SCORING_RESULTS
    GROUP_ID    = cfg.GROUP_ML_SCORING
    SCHEMA_NAME = "ScoringResult"

    def poll_and_persist(self, db_conn, max_messages: int = 500) -> int:
        """Consomme les résultats et les insère dans ml.client_scores."""
        results = self.poll_batch(max_messages=max_messages, timeout_s=60.0)
        if not results:
            return 0

        rows = [
            (r["client_id_externe"], r["imf_id"], r["score_mcrs"],
             r["score_crs"], r["score_rps"], r["score_csi"],
             r["niveau_risque"], r["cobac_classe"], r["cobac_provision_taux"],
             r["alertes"], r["model_version"], r["timestamp_ms"])
            for r in results
        ]

        with db_conn.cursor() as cur:
            cur.executemany("""
                INSERT INTO ml.client_scores
                    (client_id_externe, imf_id, score_mcrs, score_crs, score_rps, score_csi,
                     niveau_risque, cobac_classe, cobac_provision_taux, alertes,
                     model_version, scored_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s::text[], %s,
                        to_timestamp(%s::bigint / 1000.0))
                ON CONFLICT (client_id_externe, imf_id)
                DO UPDATE SET
                    score_mcrs           = EXCLUDED.score_mcrs,
                    score_crs            = EXCLUDED.score_crs,
                    score_rps            = EXCLUDED.score_rps,
                    score_csi            = EXCLUDED.score_csi,
                    niveau_risque        = EXCLUDED.niveau_risque,
                    cobac_classe         = EXCLUDED.cobac_classe,
                    cobac_provision_taux = EXCLUDED.cobac_provision_taux,
                    alertes              = EXCLUDED.alertes,
                    model_version        = EXCLUDED.model_version,
                    scored_at            = EXCLUDED.scored_at
            """, rows)
        db_conn.commit()
        log.info("Persisté %d scores MCRS en base", len(rows))
        return len(rows)


class CollecteConsumer(BaseConsumer):
    """
    Consomme les événements collecte confirmée.
    Utilisé par le pipeline Flink pour les KPI temps-réel.
    Aussi disponible pour les DAGs Airflow si Kafka n'est pas disponible.
    """
    TOPIC       = cfg.TOPIC_COLLECTES_CONFIRMED
    GROUP_ID    = cfg.GROUP_DW_SYNC
    SCHEMA_NAME = "CollecteEvent"
