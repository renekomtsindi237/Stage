"""
IMF Pipeline — Configuration Kafka centralisée.

Tous les paramètres Kafka lisibles depuis les variables d'environnement.
Compatible avec le docker-compose.streaming.yml.
"""
from __future__ import annotations

import os

# ─── Broker & Schema Registry ─────────────────────────────────────────────────
KAFKA_BOOTSTRAP_SERVERS: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9093")
SCHEMA_REGISTRY_URL: str     = os.getenv("SCHEMA_REGISTRY_URL",     "http://localhost:8081")

# ─── Topics ───────────────────────────────────────────────────────────────────
TOPIC_COLLECTES_CONFIRMED   = "imf.collectes.confirmed"
TOPIC_ALERTES_RISQUE        = "imf.alertes.risque"
TOPIC_SCORING_REQUESTS      = "imf.ml.scoring.requests"
TOPIC_SCORING_RESULTS       = "imf.ml.scoring.results"
TOPIC_CREANCES_EVENEMENTS   = "imf.creances.evenements"
TOPIC_RECOUVREMENT_ACTIONS  = "imf.recouvrement.actions"
TOPIC_KPI_REALTIME          = "imf.kpi.agents.realtime"
TOPIC_DLQ                   = "imf.dlq"

# ─── Consumer Groups ──────────────────────────────────────────────────────────
GROUP_ML_SCORING            = "imf-ml-scoring-pipeline"
GROUP_ALERTES_PROCESSOR     = "imf-alertes-processor"
GROUP_RECOUVREMENT          = "imf-recouvrement-agent"
GROUP_DW_SYNC               = "imf-dw-sync"

# ─── Producer defaults ────────────────────────────────────────────────────────
PRODUCER_CONFIG = {
    "bootstrap.servers":          KAFKA_BOOTSTRAP_SERVERS,
    "schema.registry.url":        SCHEMA_REGISTRY_URL,
    "acks":                       "all",              # durabilité maximale
    "retries":                    3,
    "linger.ms":                  10,                 # micro-batching 10ms
    "batch.size":                 16384,
    "compression.type":           "snappy",
    "enable.idempotence":         True,
    "max.in.flight.requests.per.connection": 5,
}

# ─── Consumer defaults ────────────────────────────────────────────────────────
CONSUMER_CONFIG_BASE = {
    "bootstrap.servers":          KAFKA_BOOTSTRAP_SERVERS,
    "schema.registry.url":        SCHEMA_REGISTRY_URL,
    "auto.offset.reset":          "earliest",
    "enable.auto.commit":         False,             # commit manuel (at-least-once)
    "max.poll.interval.ms":       300000,
    "session.timeout.ms":         45000,
    "heartbeat.interval.ms":      3000,
}

def consumer_config(group_id: str) -> dict:
    return {**CONSUMER_CONFIG_BASE, "group.id": group_id}
