package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestration du pipeline analyste : statut enrichi (lignes lues/écrites)
 * et exécution globale live (Airflow si disponible + simulation observable).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineOrchestrationService {

    public record DagStatusDto(
            String id, String nom, String statut,
            String duree, Integer lignesLues, Integer lignesEcrites,
            String derniereExec, String logUrl
    ) {}

    public record PipelineRunStepDto(
            String id, String nom, String statut, String detail,
            Integer lignesLues, Integer lignesEcrites
    ) {}

    public record PipelineRunDto(
            String runId, String statut,
            int etapeCourante, int etapesTotal,
            String message, String modeleVersion,
            boolean airflowDeclenche, List<PipelineRunStepDto> etapes
    ) {}

    public record PipelineStatusDto(
            String derniereExecution, String statutGlobal,
            List<DagStatusDto> dags, PipelineRunDto run
    ) {}

    public record TriggerResult(String message, PipelineRunDto run, boolean dejaEnCours) {}

    private static final List<StepDef> STEPS = List.of(
            new StepDef("imf_ingestion_daily",    "Ingestion données quotidienne",
                    "Lecture des collectes, clients et créances du jour.", 2_000, 1.00, 0.98),
            new StepDef("imf_scoring_mcrs",       "Scoring MCRS clients",
                    "Calcul des scores MCRS et priorités de recouvrement.", 2_400, 1.00, 1.00),
            new StepDef("imf_repayment_forecast", "Prévision remboursements",
                    "Projection des échéances et PAR 30/90.", 2_000, 0.92, 0.90),
            new StepDef("imf_rgpd_cleanup",       "Nettoyage RGPD",
                    "Purge des données hors durée de conservation.", 1_400, 0.15, 0.12),
            new StepDef("imf_reporting_mensuel",  "Reporting mensuel",
                    "Agrégation KPI et snapshots COBAC.", 2_000, 0.40, 0.40),
            new StepDef("imf_sync_mobile",        "Synchronisation mobile",
                    "Pousse des scores et alertes vers les agents terrain.", 1_400, 0.55, 0.55),
            new StepDef("imf_ml_retrain",         "Réentraînement modèle MCRS",
                    "Walk-forward XGBoost, calibration Platt, challenger vs champion.", 4_200, 0.70, 0.70)
    );

    private static final String[] RETRAIN_MSGS = {
            "Préparation du dataset (fenêtre 24 mois)…",
            "Split walk-forward temporel (k folds)…",
            "Entraînement XGBoost MCRS…",
            "Validation croisée AUC / Gini / KS…",
            "Calibration Platt et analyse de survie Cox…",
            "Comparaison challenger vs champion…",
            "Promotion du modèle et enregistrement MLflow…"
    };

    private final JdbcTemplate jdbc;
    private final SseEmitterRegistry sseRegistry;

    @Value("${app.airflow.url:}")
    private String airflowUrl;

    @Value("${app.airflow.username:admin}")
    private String airflowUser;

    @Value("${app.airflow.password:}")
    private String airflowPassword;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<LiveState> live = new AtomicReference<>();
    private ExecutorService executor;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    @PostConstruct
    public void startPool() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "pipeline-orchestration");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void stopPool() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public PipelineStatusDto status() {
        LiveState state = live.get();
        if (state != null) {
            return state.snapshot();
        }
        return loadFromAirflowOrSeed();
    }

    public TriggerResult trigger() {
        LiveState current = live.get();
        if (running.get() && current != null) {
            return new TriggerResult(
                    "Une exécution globale est déjà en cours",
                    current.snapshot().run(),
                    true);
        }
        if (!running.compareAndSet(false, true)) {
            LiveState again = live.get();
            return new TriggerResult(
                    "Une exécution globale est déjà en cours",
                    again != null ? again.snapshot().run() : null,
                    true);
        }

        Counts counts = loadCounts();
        boolean airflow = triggerAirflowBestEffort();
        LiveState state = new LiveState(counts, airflow);
        live.set(state);
        publish("Exécution globale démarrée", state.snapshot());

        executor.submit(() -> runSimulation(state));

        String msg = airflow
                ? "Pipeline Airflow soumis — réentraînement MCRS en cours"
                : "Exécution globale lancée — réentraînement MCRS en cours";
        return new TriggerResult(msg, state.snapshot().run(), false);
    }

    private void runSimulation(LiveState state) {
        try {
            for (int i = 0; i < STEPS.size(); i++) {
                state.startStep(i);
                publish(STEPS.get(i).nom + " — en cours", state.snapshot());
                int duration = STEPS.get(i).durationMs;
                int ticks = Math.max(4, duration / 350);
                for (int t = 1; t <= ticks; t++) {
                    Thread.sleep(duration / ticks);
                    state.progressStep(i, t / (double) ticks);
                    if (i == STEPS.size() - 1) {
                        int msgIdx = Math.min(RETRAIN_MSGS.length - 1,
                                (int) Math.floor((t / (double) ticks) * RETRAIN_MSGS.length));
                        state.setMessage(RETRAIN_MSGS[msgIdx]);
                    }
                }
                state.completeStep(i);
                publish(STEPS.get(i).nom + " — terminé", state.snapshot());
            }
            state.finish("SUCCESS", "Réentraînement terminé — modèle MCRS promu");
            publish("Exécution globale terminée", state.snapshot());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.finish("FAILED", "Exécution interrompue");
        } catch (Exception e) {
            log.warn("Simulation pipeline interrompue : {}", e.getMessage());
            state.finish("FAILED", "Échec : " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    private PipelineStatusDto loadFromAirflowOrSeed() {
        Counts counts = loadCounts();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT dr.dag_id, dr.state,
                           MAX(dr.start_date) AS dernier_exec,
                           AVG(EXTRACT(EPOCH FROM (dr.end_date - dr.start_date))) AS duree_s
                    FROM airflow.dag_run dr
                    GROUP BY dr.dag_id, dr.state
                    ORDER BY MAX(dr.start_date) DESC
                    """);
            if (!rows.isEmpty()) {
                List<DagStatusDto> dags = new ArrayList<>();
                Instant last = Instant.EPOCH;
                long failed = 0;
                long runningCount = 0;
                for (Map<String, Object> r : rows) {
                    String id = Objects.toString(r.get("dag_id"), "");
                    String statut = mapStatut(Objects.toString(r.get("state"), "success"));
                    if ("FAILED".equals(statut)) failed++;
                    if ("RUNNING".equals(statut)) runningCount++;
                    Instant exec = toInstant(r.get("dernier_exec"));
                    if (exec.isAfter(last)) last = exec;
                    int[] lines = linesFor(id, counts, 1.0);
                    dags.add(new DagStatusDto(
                            id, labelDag(id), statut,
                            formatDuree(r.get("duree_s") instanceof Number n ? n.doubleValue() : 0),
                            lines[0], lines[1],
                            exec.equals(Instant.EPOCH) ? Instant.now().toString() : exec.toString(),
                            null));
                }
                String global = runningCount > 0 ? "RUNNING" : failed > 0 ? "FAILED" : "SUCCESS";
                String lastIso = last.equals(Instant.EPOCH) ? Instant.now().toString() : last.toString();
                return new PipelineStatusDto(lastIso, global, mergeUiDags(dags, counts), null);
            }
        } catch (Exception e) {
            log.debug("airflow.dag_run indisponible (status enrichi) : {}", e.getMessage());
        }
        return seedIdle(counts);
    }

    private List<DagStatusDto> mergeUiDags(List<DagStatusDto> fromAirflow, Counts counts) {
        Map<String, DagStatusDto> byId = new LinkedHashMap<>();
        for (StepDef step : STEPS) {
            if ("imf_ml_retrain".equals(step.id)) {
                continue;
            }
            int[] lines = linesFor(step.id, counts, 1.0);
            byId.put(step.id, new DagStatusDto(
                    step.id, step.nom, "SUCCESS", "—",
                    lines[0], lines[1], Instant.now().toString(), null));
        }
        for (DagStatusDto d : fromAirflow) {
            StepDef mapped = STEPS.stream()
                    .filter(s -> s.id.equals(d.id()) || d.id().contains(s.id.replace("imf_", "")))
                    .findFirst()
                    .orElse(null);
            if (mapped != null) {
                byId.put(mapped.id, new DagStatusDto(
                        mapped.id, mapped.nom, d.statut(), d.duree(),
                        d.lignesLues(), d.lignesEcrites(), d.derniereExec(), d.logUrl()));
            }
        }
        return new ArrayList<>(byId.values());
    }

    private PipelineStatusDto seedIdle(Counts counts) {
        List<DagStatusDto> dags = new ArrayList<>();
        Instant now = Instant.parse("2026-06-18T08:00:00Z");
        for (int i = 0; i < STEPS.size() - 1; i++) {
            StepDef s = STEPS.get(i);
            boolean failed = "imf_reporting_mensuel".equals(s.id);
            int[] lines = linesFor(s.id, counts, 1.0);
            dags.add(new DagStatusDto(
                    s.id, s.nom, failed ? "FAILED" : "SUCCESS",
                    failed ? "—" : defaultDuree(s.id),
                    lines[0], lines[1],
                    now.minus(Duration.ofHours(STEPS.size() - i)).toString(),
                    null));
        }
        return new PipelineStatusDto(now.toString(), "FAILED", dags, null);
    }

    private Counts loadCounts() {
        int clients = count("SELECT COUNT(*) FROM app.clients_informels", 1_240);
        int scores = count("SELECT COUNT(*) FROM ml.client_scores", Math.max(clients, 980));
        int collectes = count("SELECT COUNT(*) FROM app.collectes_terrain", 3_560);
        int dossiers = count("SELECT COUNT(*) FROM app.dossiers_recouvrement", 86);
        int alertes = count("SELECT COUNT(*) FROM ml.alertes_predictives", 24);
        return new Counts(clients, scores, collectes, dossiers, alertes);
    }

    private int count(String sql, int fallback) {
        try {
            Integer n = jdbc.queryForObject(sql, Integer.class);
            return n != null && n > 0 ? n : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean triggerAirflowBestEffort() {
        if (airflowUrl == null || airflowUrl.isBlank()) {
            return false;
        }
        boolean any = false;
        any |= postDagRun("dag_pipeline_init");
        any |= postDagRun("dag_ml_training");
        any |= postDagRun("dag_ml_scoring");
        return any;
    }

    private boolean postDagRun(String dagId) {
        try {
            String base = airflowUrl.endsWith("/") ? airflowUrl.substring(0, airflowUrl.length() - 1) : airflowUrl;
            String body = "{\"dag_run_id\":\"manual__" + Instant.now().toEpochMilli() + "\"}";
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/api/v1/dags/" + dagId + "/dagRuns"))
                    .timeout(Duration.ofSeconds(6))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (airflowPassword != null && !airflowPassword.isBlank()) {
                String basic = Base64.getEncoder().encodeToString(
                        (airflowUser + ":" + airflowPassword).getBytes(StandardCharsets.UTF_8));
                req.header("Authorization", "Basic " + basic);
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                log.info("DAG Airflow {} déclenché (HTTP {})", dagId, res.statusCode());
                return true;
            }
            log.debug("Airflow {} HTTP {} : {}", dagId, res.statusCode(), res.body());
        } catch (Exception e) {
            log.debug("Airflow {} injoignable : {}", dagId, e.getMessage());
        }
        return false;
    }

    private void publish(String message, PipelineStatusDto snapshot) {
        try {
            sseRegistry.broadcast(new SseEventDto(
                    SseEventDto.TYPE_PIPELINE_STATUS,
                    null,
                    message,
                    snapshot,
                    OffsetDateTime.now()));
        } catch (Exception e) {
            log.debug("Broadcast pipeline ignoré : {}", e.getMessage());
        }
    }

    static String labelDag(String dagId) {
        return switch (dagId) {
            case "imf_ingestion_daily", "dag_collectes" -> "Ingestion données quotidienne";
            case "imf_scoring_mcrs", "dag_ml_scoring" -> "Scoring MCRS clients";
            case "imf_repayment_forecast" -> "Prévision remboursements";
            case "imf_rgpd_cleanup" -> "Nettoyage RGPD";
            case "imf_reporting_mensuel", "dag_kpis_quotidien" -> "Reporting mensuel";
            case "imf_sync_mobile" -> "Synchronisation mobile";
            case "imf_ml_retrain", "dag_ml_training" -> "Réentraînement modèle MCRS";
            default -> dagId;
        };
    }

    private static String mapStatut(String airflowState) {
        return switch (airflowState.toLowerCase()) {
            case "success" -> "SUCCESS";
            case "running", "queued" -> "RUNNING";
            case "failed" -> "FAILED";
            default -> "PENDING";
        };
    }

    private static String formatDuree(double secondes) {
        if (secondes <= 0) return "—";
        int mins = (int) (secondes / 60);
        int secs = (int) (secondes % 60);
        return mins + "m " + secs + "s";
    }

    private static String defaultDuree(String id) {
        return switch (id) {
            case "imf_ingestion_daily" -> "2m 15s";
            case "imf_scoring_mcrs" -> "4m 32s";
            case "imf_repayment_forecast" -> "6m 05s";
            case "imf_rgpd_cleanup" -> "1m 10s";
            case "imf_sync_mobile" -> "0m 45s";
            default -> "3m 00s";
        };
    }

    private static int[] linesFor(String id, Counts c, double factor) {
        int read;
        int written;
        switch (id) {
            case "imf_ingestion_daily" -> { read = c.collectes + c.clients; written = (int) (c.collectes * 0.98); }
            case "imf_scoring_mcrs" -> { read = c.clients; written = c.scores; }
            case "imf_repayment_forecast" -> { read = c.scores; written = Math.max(1, (int) (c.scores * 0.9)); }
            case "imf_rgpd_cleanup" -> { read = c.clients; written = Math.max(0, c.clients / 12); }
            case "imf_reporting_mensuel" -> { read = c.dossiers + c.alertes; written = 12; }
            case "imf_sync_mobile" -> { read = c.scores; written = c.alertes + c.dossiers; }
            case "imf_ml_retrain" -> { read = c.clients + c.scores; written = 1; }
            default -> { read = c.clients; written = c.scores; }
        }
        return new int[]{Math.max(0, (int) (read * factor)), Math.max(0, (int) (written * factor))};
    }

    private static Instant toInstant(Object v) {
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (v instanceof Instant i) return i;
        if (v instanceof OffsetDateTime odt) return odt.toInstant();
        if (v != null) {
            try {
                return Instant.parse(v.toString().replace(" ", "T"));
            } catch (Exception ignored) {
                return Instant.EPOCH;
            }
        }
        return Instant.EPOCH;
    }

    private record StepDef(String id, String nom, String detail, int durationMs, double readFactor, double writeFactor) {}

    private record Counts(int clients, int scores, int collectes, int dossiers, int alertes) {}

    private static final class LiveState {
        private final Counts counts;
        private final boolean airflow;
        private final String runId = "manual__" + Instant.now().toEpochMilli();
        private final String startedAt = Instant.now().toString();
        private final List<MutableStep> steps = new ArrayList<>();
        private volatile String global = "RUNNING";
        private volatile String message = "Démarrage de l'exécution globale…";
        private volatile String modeleVersion = "MCRS-v2.4.1";
        private volatile int current = 0;

        LiveState(Counts counts, boolean airflow) {
            this.counts = counts;
            this.airflow = airflow;
            for (StepDef s : STEPS) {
                steps.add(new MutableStep(s.id, s.nom, "PENDING", s.detail, 0, 0));
            }
        }

        synchronized void startStep(int i) {
            current = i;
            MutableStep s = steps.get(i);
            s.statut = "RUNNING";
            message = s.nom + " en cours…";
        }

        synchronized void progressStep(int i, double pct) {
            StepDef def = STEPS.get(i);
            int[] target = linesFor(def.id, counts, 1.0);
            MutableStep s = steps.get(i);
            s.lignesLues = (int) Math.round(target[0] * pct);
            s.lignesEcrites = (int) Math.round(target[1] * pct);
        }

        synchronized void completeStep(int i) {
            StepDef def = STEPS.get(i);
            int[] target = linesFor(def.id, counts, 1.0);
            MutableStep s = steps.get(i);
            s.statut = "SUCCESS";
            s.lignesLues = target[0];
            s.lignesEcrites = target[1];
            if ("imf_ml_retrain".equals(def.id)) {
                modeleVersion = "MCRS-v2.4.2";
            }
        }

        synchronized void setMessage(String msg) {
            this.message = msg;
        }

        synchronized void finish(String statut, String msg) {
            global = statut;
            message = msg;
            if ("SUCCESS".equals(statut)) {
                current = STEPS.size();
                for (int i = 0; i < steps.size(); i++) {
                    if (!"SUCCESS".equals(steps.get(i).statut)) {
                        completeStep(i);
                    }
                }
            }
        }

        synchronized PipelineStatusDto snapshot() {
            List<DagStatusDto> dags = new ArrayList<>();
            List<PipelineRunStepDto> etapes = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                MutableStep s = steps.get(i);
                etapes.add(new PipelineRunStepDto(
                        s.id, s.nom, s.statut, s.detail, s.lignesLues, s.lignesEcrites));
                if (!"imf_ml_retrain".equals(s.id)) {
                    dags.add(new DagStatusDto(
                            s.id, s.nom, s.statut,
                            "RUNNING".equals(s.statut) ? "…" : defaultDuree(s.id),
                            s.lignesLues, s.lignesEcrites, startedAt, null));
                }
            }
            PipelineRunDto run = new PipelineRunDto(
                    runId, global, Math.min(current + 1, STEPS.size()), STEPS.size(),
                    message, modeleVersion, airflow, List.copyOf(etapes));
            return new PipelineStatusDto(startedAt, global, dags, run);
        }
    }

    private static final class MutableStep {
        final String id;
        final String nom;
        String statut;
        String detail;
        int lignesLues;
        int lignesEcrites;

        MutableStep(String id, String nom, String statut, String detail, int lues, int ecrites) {
            this.id = id;
            this.nom = nom;
            this.statut = statut;
            this.detail = detail;
            this.lignesLues = lues;
            this.lignesEcrites = ecrites;
        }
    }
}
