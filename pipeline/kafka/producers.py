"""
IMF Pipeline — Producteurs Kafka (Python)
==========================================

Producteurs Avro-sérialisés pour les 4 types d'événements principaux :
  - CollecteProducer       → imf.collectes.confirmed
  - ScoringRequestProducer → imf.ml.scoring.requests
  - AlerteProducer         → imf.alertes.risque
  - CreanceProducer        → imf.creances.evenements

Usage :
  from pipeline.kafka.producers import CollecteProducer
  with CollecteProducer() as producer:
      producer.send(collecte_dict)
"""
from __future__ import annotations

import logging
import time
import uuid
from typing import Any

from . import config as cfg

log = logging.getLogger("imf.kafka.producers")


def _make_producer():
    """Crée un producteur confluent-kafka avec serialisation Avro."""
    try:
        from confluent_kafka import SerializingProducer
        from confluent_kafka.schema_registry import SchemaRegistryClient
        from confluent_kafka.schema_registry.avro import AvroSerializer
        from confluent_kafka.serialization import StringSerializer
        return SerializingProducer, SchemaRegistryClient, AvroSerializer, StringSerializer
    except ImportError:
        raise ImportError(
            "confluent-kafka manquant. Installer : pip install confluent-kafka[avro]"
        )


class BaseProducer:
    """Producteur Kafka Avro de base avec retry et DLQ."""

    TOPIC: str
    SCHEMA_NAME: str

    def __init__(self) -> None:
        SerializingProducer, SchemaRegistryClient, AvroSerializer, StringSerializer = _make_producer()
        import json, pathlib

        schema_path = pathlib.Path(__file__).parent.parent.parent / "schemas" / "avro" / f"{self.SCHEMA_NAME}.avsc"
        avro_schema_str = schema_path.read_text(encoding="utf-8")

        sr_client     = SchemaRegistryClient({"url": cfg.SCHEMA_REGISTRY_URL})
        avro_ser      = AvroSerializer(sr_client, avro_schema_str)
        str_ser       = StringSerializer("utf_8")

        producer_cfg  = {**cfg.PRODUCER_CONFIG}
        producer_cfg.update({
            "key.serializer":   str_ser,
            "value.serializer": avro_ser,
        })
        self._producer = SerializingProducer(producer_cfg)
        log.info("Producteur initialisé — topic=%s", self.TOPIC)

    def _delivery_report(self, err, msg) -> None:
        if err:
            log.error("Erreur livraison Kafka [%s] : %s", self.TOPIC, err)
        else:
            log.debug("Message livré [%s] partition=%d offset=%d",
                      self.TOPIC, msg.partition(), msg.offset())

    def send(self, record: dict[str, Any], key: str | None = None) -> None:
        """Envoie un message Avro au topic. key = client_id_externe par défaut."""
        k = key or record.get("client_id_externe") or str(uuid.uuid4())
        record.setdefault("event_id", str(uuid.uuid4()))
        record.setdefault("timestamp_ms", int(time.time() * 1000))

        self._producer.produce(
            topic=self.TOPIC,
            key=k,
            value=record,
            on_delivery=self._delivery_report,
        )
        self._producer.poll(0)  # déclenche les callbacks

    def flush(self, timeout: float = 10.0) -> None:
        self._producer.flush(timeout)

    def __enter__(self): return self

    def __exit__(self, *args):
        self.flush()


class CollecteProducer(BaseProducer):
    """
    Émetteur d'événements collecte confirmée.
    Appelé par : dag_collectes.py, CollecteService.java (Spring Boot)
    """
    TOPIC       = cfg.TOPIC_COLLECTES_CONFIRMED
    SCHEMA_NAME = "CollecteEvent"

    def send_collecte(self, collecte_id: str, client_id: str, agent_id: str,
                       agence_id: str, imf_id: int, region_id: str,
                       montant: float, canal: str, statut: str = "CONFIRMEE",
                       reference_momo: str | None = None,
                       latitude: float | None = None,
                       longitude: float | None = None) -> None:
        from datetime import date
        record = {
            "event_id":          str(uuid.uuid4()),
            "collecte_id":       collecte_id,
            "client_id_externe": client_id,
            "agent_id":          agent_id,
            "agence_id":         agence_id,
            "imf_id":            imf_id,
            "region_id":         region_id,
            "montant":           montant,
            "canal":             canal,
            "statut":            statut,
            "reference_momo":    reference_momo,
            "latitude":          latitude,
            "longitude":         longitude,
            "timestamp_ms":      int(time.time() * 1000),
            "date_collecte":     (date.today() - date(1970, 1, 1)).days,  # Avro date = days since epoch
        }
        self.send(record, key=client_id)
        log.info("CollecteEvent envoyé — client=%s montant=%.0f canal=%s",
                 client_id, montant, canal)


class ScoringRequestProducer(BaseProducer):
    """
    Émetteur de demandes de scoring MCRS vers le service ML.
    Appelé par : dag_ml_scoring.py
    """
    TOPIC       = cfg.TOPIC_SCORING_REQUESTS
    SCHEMA_NAME = "ScoringRequest"

    def send_from_features_row(self, request_id: str, client_id: str,
                                imf_id: int, region_id: str,
                                features: dict[str, Any]) -> None:
        record = {
            "request_id":        request_id,
            "client_id_externe": client_id,
            "imf_id":            imf_id,
            "region_id":         region_id,
            "timestamp_ms":      int(time.time() * 1000),
            **features,
        }
        self.send(record, key=client_id)


class AlerteProducer(BaseProducer):
    """
    Émetteur d'alertes risque MCRS.
    Appelé par : dag_ml_scoring.py, service FastAPI ML, job Flink
    """
    TOPIC       = cfg.TOPIC_ALERTES_RISQUE
    SCHEMA_NAME = "AlerteRisque"

    def send_alerte(self, client_id: str, imf_id: int, agence_id: str,
                     region_id: str, score_mcrs: float, score_crs: float,
                     score_rps: float, score_csi: float,
                     niveau_risque: str, cobac_classe: str,
                     cobac_provision_taux: float,
                     types_alertes: list[str], jours_retard: int,
                     action_recommandee: str = "RELANCE_PREVENTIVE",
                     source: str = "dag_ml_scoring") -> None:
        record = {
            "event_id":          str(uuid.uuid4()),
            "alerte_id":         str(uuid.uuid4()),
            "client_id_externe": client_id,
            "imf_id":            imf_id,
            "agence_id":         agence_id,
            "region_id":         region_id,
            "score_mcrs":        score_mcrs,
            "score_crs":         score_crs,
            "score_rps":         score_rps,
            "score_csi":         score_csi,
            "niveau_risque":     niveau_risque,
            "cobac_classe":      cobac_classe,
            "cobac_provision_taux": cobac_provision_taux,
            "types_alertes":     types_alertes,
            "jours_retard":      jours_retard,
            "action_recommandee": action_recommandee,
            "timestamp_ms":      int(time.time() * 1000),
            "source":            source,
        }
        self.send(record, key=client_id)
        log.info("AlerteRisque envoyée — client=%s MCRS=%.4f niveau=%s",
                 client_id, score_mcrs, niveau_risque)


class CreanceProducer(BaseProducer):
    """
    Émetteur d'événements sur les créances (paiements, retards, restructurations).
    Appelé par : dag_recouvrement.py
    """
    TOPIC       = cfg.TOPIC_CREANCES_EVENEMENTS
    SCHEMA_NAME = "CreanceEvenement"

    def send_evenement(self, creance_id: str, client_id: str, imf_id: int,
                        agence_id: str, type_evenement: str,
                        montant_encours: float, montant_evenement: float,
                        jours_retard: int, cobac_avant: str, cobac_apres: str,
                        taux_provision: float) -> None:
        from datetime import date
        record = {
            "event_id":          str(uuid.uuid4()),
            "creance_id":        creance_id,
            "client_id_externe": client_id,
            "imf_id":            imf_id,
            "agence_id":         agence_id,
            "type_evenement":    type_evenement,
            "montant_encours":   montant_encours,
            "montant_evenement": montant_evenement,
            "jours_retard":      jours_retard,
            "cobac_classe_avant": cobac_avant,
            "cobac_classe_apres": cobac_apres,
            "taux_provision":    taux_provision,
            "timestamp_ms":      int(time.time() * 1000),
            "date_evenement":    (date.today() - date(1970, 1, 1)).days,
        }
        self.send(record, key=client_id)
