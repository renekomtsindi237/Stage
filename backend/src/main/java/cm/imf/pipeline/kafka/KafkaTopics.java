package cm.imf.pipeline.kafka;

/**
 * Constantes des topics Kafka IMF Pipeline.
 *
 * Synchronisées avec pipeline/kafka/config.py (Python) et
 * docker-compose.streaming.yml (kafka-init).
 *
 * Convention de nommage : imf.<domaine>.<entite>
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    // ─── Collectes ─────────────────────────────────────────────────────────
    public static final String COLLECTES_CONFIRMED   = "imf.collectes.confirmed";

    // ─── ML Scoring ────────────────────────────────────────────────────────
    public static final String SCORING_REQUESTS      = "imf.ml.scoring.requests";
    public static final String SCORING_RESULTS       = "imf.ml.scoring.results";

    // ─── Alertes risque ────────────────────────────────────────────────────
    public static final String ALERTES_RISQUE        = "imf.alertes.risque";

    // ─── Créances ──────────────────────────────────────────────────────────
    public static final String CREANCES_EVENEMENTS   = "imf.creances.evenements";

    // ─── Recouvrement ──────────────────────────────────────────────────────
    public static final String RECOUVREMENT_ACTIONS  = "imf.recouvrement.actions";

    // ─── KPI temps-réel (Flink → Spring SSE) ──────────────────────────────
    public static final String KPI_AGENTS_REALTIME   = "imf.kpi.agents.realtime";

    // ─── Dead Letter Queue ─────────────────────────────────────────────────
    public static final String DLQ                   = "imf.dlq";
}
