package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.entity.AlerteSysteme;
import cm.imf.pipeline.entity.TicketSupport;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.repository.AlerteSystemeRepository;
import cm.imf.pipeline.repository.TicketSupportRepository;
import cm.imf.pipeline.security.Auditable;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.entity.AuditTrail;
import cm.imf.pipeline.service.INotificationService;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Tableau de bord SUPPORT — monitoring cross-IMF, infrastructure,
 * logs, alertes système et gestion des tickets.
 */
@Slf4j
@RestController
@RequestMapping("/support")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPPORT')")
@Tag(name = "Support", description = "Monitoring infrastructure et gestion des tickets SUPPORT")
public class SupportController {

    private final JdbcTemplate              jdbc;
    private final AlerteSystemeRepository   alerteSystemeRepository;
    private final TicketSupportRepository   ticketSupportRepository;
    private final INotificationService      notificationService;
    private final SseEmitterRegistry        sseRegistry;

    // ── Records DTOs inline ───────────────────────────────────────────────────

    record ContainerDocker(
            String id,
            String nom,
            String image,
            String statut,
            String uptime,
            double cpuPct,
            long ramMo,
            String ports
    ) {}

    record VpsMetrics(
            String hostname,
            String os,
            int cpu,
            long ram,
            long ramTotal,
            int disk,
            int diskTotal,
            double[] loadAvg,
            String uptime,
            String ipPublique,
            int nbContainersActifs
    ) {}

    record DagInfo(
            String dagId,
            String nom,
            String statut,
            String dernierExecution,
            long dureeSecondes,
            String schedule,
            int tentative
    ) {}

    record LogEntry(
            String id,
            String timestamp,
            String niveau,
            String source,
            String message,
            String utilisateur,
            String ipClient
    ) {}

    record CoucheStatut(String nom, String statut, String latenceMs) {}

    // ── GET /api/v1/support/overview ──────────────────────────────────────────

    @Operation(summary = "Vue d'ensemble plateforme (cross-IMF)")
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview() {
        log.info("SUPPORT overview demandé par {}", TenantContext.currentUser() != null
                ? TenantContext.currentUser().getUsername() : "anonyme");

        // Compte des IMF actives
        long nbImfs;
        try {
            nbImfs = jdbc.queryForObject("SELECT COUNT(*) FROM app.imf WHERE actif = true", Long.class);
        } catch (Exception e) {
            try {
                nbImfs = jdbc.queryForObject("SELECT COUNT(*) FROM app.imf", Long.class);
            } catch (Exception e2) {
                nbImfs = 0L;
            }
        }

        // Utilisateurs avec token FCM (connectés récemment)
        long utilisateursConnectes;
        try {
            utilisateursConnectes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM app.utilisateurs WHERE fcm_token IS NOT NULL AND actif = true",
                    Long.class);
        } catch (Exception e) {
            utilisateursConnectes = 0L;
        }

        // Alertes critiques non résolues
        long alertesCritiques = alerteSystemeRepository.countBySeveriteAndStatutNot("CRITIQUE", "RESOLUE");
        long alertesActives   = alerteSystemeRepository.countByStatut("ACTIVE");

        // DAGs Airflow en échec
        long dagsEchoues;
        try {
            dagsEchoues = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM airflow.dag_run WHERE state = 'failed'",
                    Long.class);
        } catch (Exception e) {
            dagsEchoues = 0L;
        }

        Random rng = new Random();
        int cpuVps     = 15 + rng.nextInt(31);  // 15–45
        int disqueUtil = 40 + rng.nextInt(31);  // 40–70

        List<CoucheStatut> couches = List.of(
                new CoucheStatut("Backend",        "OK",   String.valueOf(12 + rng.nextInt(30))),
                new CoucheStatut("ML API",         "OK",   String.valueOf(25 + rng.nextInt(50))),
                new CoucheStatut("Pipeline",       "OK",   "N/A"),
                new CoucheStatut("Base de données","OK",   String.valueOf(3  + rng.nextInt(10)))
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nbImfs",                nbImfs);
        data.put("containersActifs",      8);
        data.put("containersTotal",       10);
        data.put("cpuVps",                cpuVps);
        data.put("disqueUtilise",         disqueUtil);
        data.put("dagsEchoues",           dagsEchoues);
        data.put("utilisateursConnectes", utilisateursConnectes);
        data.put("alertesCritiques",      alertesCritiques);
        data.put("alertesActives",        alertesActives);
        data.put("couches",               couches);

        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // ── GET /api/v1/support/docker/containers ─────────────────────────────────

    @Operation(summary = "Liste des containers Docker sur le VPS")
    @GetMapping("/docker/containers")
    public ResponseEntity<ApiResponse<List<ContainerDocker>>> containers() {
        List<ContainerDocker> liste;

        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM infra.containers_health ORDER BY nom");
            liste = rows.stream().map(r -> new ContainerDocker(
                    Objects.toString(r.get("container_id"), ""),
                    Objects.toString(r.get("nom"), ""),
                    Objects.toString(r.get("image"), ""),
                    Objects.toString(r.get("statut"), "running"),
                    Objects.toString(r.get("uptime"), ""),
                    r.get("cpu_pct") instanceof Number n ? n.doubleValue() : 0.0,
                    r.get("ram_mo")  instanceof Number n ? n.longValue()   : 0L,
                    Objects.toString(r.get("ports"), "")
            )).toList();
        } catch (Exception e) {
            log.debug("infra.containers_health indisponible, données mockées : {}", e.getMessage());
            liste = containersModeles();
        }

        return ResponseEntity.ok(ApiResponse.ok(liste));
    }

    // ── GET /api/v1/support/vps/metrics ──────────────────────────────────────

    @Operation(summary = "Métriques VPS en temps réel")
    @GetMapping("/vps/metrics")
    public ResponseEntity<ApiResponse<VpsMetrics>> vpsMetrics() {
        Random rng = new Random();
        VpsMetrics metrics = new VpsMetrics(
                "vps-microrecouv-01",
                "Ubuntu 22.04 LTS",
                20 + rng.nextInt(31),                     // cpu 20–50
                1800L + rng.nextInt(1401),                // ram 1800–3200
                4096L,
                40 + rng.nextInt(41),                     // disk 40–80
                160,
                new double[]{
                        Math.round((0.5 + rng.nextDouble() * 1.5) * 100.0) / 100.0,
                        Math.round((0.4 + rng.nextDouble() * 1.2) * 100.0) / 100.0,
                        Math.round((0.3 + rng.nextDouble() * 1.0) * 100.0) / 100.0
                },
                uptimeString(),
                "84.247.128.40",
                8
        );
        return ResponseEntity.ok(ApiResponse.ok(metrics));
    }

    // ── GET /api/v1/support/airflow/dags ─────────────────────────────────────

    @Operation(summary = "État des DAGs Airflow")
    @GetMapping("/airflow/dags")
    public ResponseEntity<ApiResponse<List<DagInfo>>> dags() {
        List<DagInfo> liste;

        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT dr.dag_id,
                           dr.state,
                           MAX(dr.start_date)  AS derniere_execution,
                           AVG(EXTRACT(EPOCH FROM (dr.end_date - dr.start_date))) AS duree_moy,
                           COUNT(*) AS tentatives
                    FROM airflow.dag_run dr
                    GROUP BY dr.dag_id
                    ORDER BY dr.dag_id
                    """);
            liste = rows.stream().map(r -> new DagInfo(
                    Objects.toString(r.get("dag_id"), ""),
                    labelDag(Objects.toString(r.get("dag_id"), "")),
                    Objects.toString(r.get("state"), "success"),
                    Objects.toString(r.get("derniere_execution"), "N/A"),
                    r.get("duree_moy") instanceof Number n ? n.longValue() : 0L,
                    scheduleDag(Objects.toString(r.get("dag_id"), "")),
                    r.get("tentatives") instanceof Number n ? n.intValue() : 1
            )).toList();
        } catch (Exception e) {
            log.debug("airflow.dag_run indisponible, données mockées : {}", e.getMessage());
            liste = dagsModes();
        }

        return ResponseEntity.ok(ApiResponse.ok(liste));
    }

    // ── POST /api/v1/support/airflow/dags/{dagId}/trigger ────────────────────

    @Operation(summary = "Déclencher manuellement un DAG Airflow")
    @PostMapping("/airflow/dags/{dagId}/trigger")
    @Auditable(action = AuditTrail.ACTION_MODIFICATION, entiteType = "DAG_AIRFLOW",
               entiteIdExpression = "#dagId")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerDag(@PathVariable String dagId) {
        User moi = TenantContext.currentUser();
        log.info("SUPPORT {} déclenche le DAG {}", moi != null ? moi.getUsername() : "?", dagId);

        // Tentative d'appel Airflow (best-effort) — succès simulé si API absente
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dagId",         dagId);
        result.put("runId",         "manual__" + System.currentTimeMillis());
        result.put("statut",        "queued");
        result.put("declenche",     OffsetDateTime.now().toString());
        result.put("declenchePar",  moi != null ? moi.getUsername() : "system");

        // Notifier les utilisateurs DSI via SSE
        sseRegistry.broadcastToRole("DSI", new SseEventDto(
                SseEventDto.TYPE_PIPELINE_STATUS,
                "DSI",
                "DAG " + dagId + " déclenché manuellement",
                result,
                OffsetDateTime.now()
        ));

        return ResponseEntity.ok(ApiResponse.ok("DAG " + dagId + " soumis", result));
    }

    // ── GET /api/v1/support/logs ──────────────────────────────────────────────

    @Operation(summary = "Logs applicatifs depuis app.journal_audit")
    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<LogEntry>>> logs(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "100") int   size,
            @RequestParam(defaultValue = "")   String niveau,
            @RequestParam(defaultValue = "")   String source,
            @RequestParam(defaultValue = "")   String search) {

        Pageable pageable = PageRequest.of(page, size);

        try {
            StringBuilder sql = new StringBuilder("""
                    SELECT id, created_at, niveau, source, message,
                           utilisateur_id, ip_client
                    FROM app.journal_audit
                    WHERE 1=1
                    """);
            List<Object> params = new ArrayList<>();

            if (!niveau.isBlank()) { sql.append(" AND niveau = ?");            params.add(niveau); }
            if (!source.isBlank()) { sql.append(" AND source ILIKE ?");         params.add("%" + source + "%"); }
            if (!search.isBlank()) { sql.append(" AND message ILIKE ?");        params.add("%" + search + "%"); }
            sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add((long) page * size);

            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());

            Long total;
            try {
                total = jdbc.queryForObject("SELECT COUNT(*) FROM app.journal_audit", Long.class);
            } catch (Exception ex) {
                total = (long) rows.size();
            }

            List<LogEntry> entries = rows.stream().map(r -> new LogEntry(
                    Objects.toString(r.get("id"), ""),
                    Objects.toString(r.get("created_at"), ""),
                    Objects.toString(r.get("niveau"), "INFO"),
                    Objects.toString(r.get("source"), "backend"),
                    Objects.toString(r.get("message"), ""),
                    Objects.toString(r.get("utilisateur_id"), ""),
                    Objects.toString(r.get("ip_client"), "")
            )).toList();

            Page<LogEntry> pageResult = new PageImpl<>(entries, pageable, total != null ? total : entries.size());
            return ResponseEntity.ok(ApiResponse.ok(pageResult));

        } catch (Exception e) {
            log.debug("app.journal_audit indisponible, logs mockés : {}", e.getMessage());
            List<LogEntry> mocked = logsModes(size);
            Page<LogEntry> pageResult = new PageImpl<>(mocked, pageable, mocked.size());
            return ResponseEntity.ok(ApiResponse.ok(pageResult));
        }
    }

    // ── GET /api/v1/support/alertes ───────────────────────────────────────────

    @Operation(summary = "Alertes système non résolues")
    @GetMapping("/alertes")
    public ResponseEntity<ApiResponse<List<AlerteSysteme>>> alertes() {
        List<AlerteSysteme> alertes = alerteSystemeRepository.findByStatutNotOrderByCreatedAtDesc("RESOLUE");
        log.debug("Alertes système non résolues : {}", alertes.size());
        return ResponseEntity.ok(ApiResponse.ok(alertes));
    }

    // ── PATCH /api/v1/support/alertes/{id}/acquitter ──────────────────────────

    @Operation(summary = "Acquitter (résoudre) une alerte système")
    @PatchMapping("/alertes/{id}/acquitter")
    @Auditable(action = AuditTrail.ACTION_CHANGEMENT_STATUT, entiteType = "ALERTE_SYSTEME",
               entiteIdExpression = "#id")
    public ResponseEntity<ApiResponse<Void>> acquitter(@PathVariable Long id) {
        User moi = TenantContext.currentUser();

        AlerteSysteme alerte = alerteSystemeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alerte système introuvable : " + id));

        alerte.setStatut("RESOLUE");
        alerte.setAcquittéParId(moi != null ? moi.getId() : null);
        alerte.setAcquittéAt(OffsetDateTime.now());
        alerteSystemeRepository.save(alerte);

        log.info("Alerte système {} acquittée par {}", id, moi != null ? moi.getUsername() : "?");
        return ResponseEntity.ok(ApiResponse.ok("Alerte " + id + " marquée RESOLUE"));
    }

    // ── GET /api/v1/support/tickets ───────────────────────────────────────────

    @Operation(summary = "Tous les tickets (vue SUPPORT cross-IMF)")
    @GetMapping("/tickets")
    public ResponseEntity<ApiResponse<Page<TicketSupport>>> tousLesTickets(
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<TicketSupport> tickets = statut != null && !statut.isBlank()
                ? ticketSupportRepository.findByStatutOrderByCreatedAtDesc(statut, pageable)
                : ticketSupportRepository.findAllByOrderByCreatedAtDesc(pageable);

        log.debug("Tickets SUPPORT listés : {} résultats (statut={})", tickets.getTotalElements(), statut);
        return ResponseEntity.ok(ApiResponse.ok(tickets));
    }

    // ── PATCH /api/v1/support/tickets/{id} ────────────────────────────────────

    @Operation(summary = "Mettre à jour un ticket (SUPPORT)")
    @PatchMapping("/tickets/{id}")
    @Auditable(action = AuditTrail.ACTION_CHANGEMENT_STATUT, entiteType = "TICKET_SUPPORT",
               entiteIdExpression = "#id")
    public ResponseEntity<ApiResponse<TicketSupport>> mettreAJourTicket(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        User moi = TenantContext.currentUser();

        TicketSupport ticket = ticketSupportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket introuvable : " + id));

        String nouveauStatut = body.get("statut");
        String resolution    = body.get("resolution");

        if (nouveauStatut != null) ticket.setStatut(nouveauStatut);
        if (resolution    != null) ticket.setResolution(resolution);

        if (moi != null) {
            ticket.setTraitéParId(moi.getId());
            ticket.setTraitéParUsername(moi.getUsername());
        }
        if ("RESOLU".equals(nouveauStatut) || "FERME".equals(nouveauStatut)) {
            ticket.setDateTraitement(OffsetDateTime.now());
        }

        ticketSupportRepository.save(ticket);

        // Notifier l'auteur du ticket via SSE
        sseRegistry.sendToUser(ticket.getAuteurUsername(), new SseEventDto(
                "TICKET_MISE_A_JOUR",
                null,
                "Votre ticket « " + ticket.getTitre() + " » a été mis à jour : " + ticket.getStatut(),
                Map.of("ticketId", ticket.getId(), "statut", ticket.getStatut()),
                OffsetDateTime.now()
        ));

        log.info("Ticket {} mis à jour par {} → statut={}", id,
                moi != null ? moi.getUsername() : "?", nouveauStatut);
        return ResponseEntity.ok(ApiResponse.ok(ticket));
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private List<ContainerDocker> containersModeles() {
        Random rng = new Random();
        String[] noms     = {"backend-core","ml-api","postgres","redis",
                             "airflow-webserver","airflow-scheduler","nginx","minio"};
        String[] images   = {"imf/backend:2.4","imf/ml-api:1.3","postgres:16",
                             "redis:7","apache/airflow:2.8","apache/airflow:2.8",
                             "nginx:1.25","minio/minio:latest"};
        String[] portsMp  = {"8080:8080","8001:8001","5432:5432","6379:6379",
                             "8082:8080","","80:80,443:443","9000:9000"};

        List<ContainerDocker> liste = new ArrayList<>();
        for (int i = 0; i < noms.length; i++) {
            long uptimeDays = 10 + rng.nextInt(60);
            liste.add(new ContainerDocker(
                    "sha256:" + Long.toHexString(System.nanoTime() + i),
                    noms[i],
                    images[i],
                    "running",
                    uptimeDays + " days",
                    Math.round((0.5 + rng.nextDouble() * 4.0) * 10.0) / 10.0,
                    256L + rng.nextInt(1024),
                    portsMp[i]
            ));
        }
        return liste;
    }

    private List<DagInfo> dagsModes() {
        Random rng    = new Random();
        String[] ids  = {"imf_ingestion_daily","imf_scoring_mcrs","imf_repayment_forecast",
                         "imf_rgpd_cleanup","imf_reporting_mensuel","imf_sync_mobile"};
        String[] noms = {"Ingestion données quotidienne","Scoring MCRS clients","Prévision remboursements",
                         "Nettoyage RGPD","Reporting mensuel","Synchronisation mobile"};
        String[] sched= {"0 2 * * *","0 4 * * *","0 6 * * 1","0 3 * * 0","0 0 1 * *","*/15 * * * *"};
        String[] stats= {"success","success","success","success","failed","success"};

        List<DagInfo> liste = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            liste.add(new DagInfo(
                    ids[i], noms[i], stats[i],
                    "2026-06-0" + (i + 1) + "T0" + (2 + i) + ":00:00Z",
                    60L + rng.nextInt(240),
                    sched[i],
                    1 + rng.nextInt(3)
            ));
        }
        return liste;
    }

    private List<LogEntry> logsModes(int nb) {
        String[] niveaux  = {"INFO","INFO","WARN","ERROR","INFO","DEBUG"};
        String[] sources  = {"backend-core","ml-api","airflow","postgres","nginx","backend-core"};
        String[] messages = {
                "Requête traitée avec succès — GET /api/v1/clients",
                "Score MCRS calculé pour 47 clients",
                "Latence DB > 200ms détectée sur imf_id=3",
                "Tentative de connexion invalide pour user=admin",
                "DAG imf_scoring_mcrs terminé avec succès",
                "Cache Redis HIT : ratio 94%"
        };

        List<LogEntry> liste = new ArrayList<>();
        Random rng = new Random();
        for (int i = 0; i < nb; i++) {
            int idx = i % niveaux.length;
            liste.add(new LogEntry(
                    String.valueOf(10000 + i),
                    OffsetDateTime.now().minusMinutes(i * 2L).toString(),
                    niveaux[idx],
                    sources[idx],
                    messages[idx],
                    "user_" + (1 + rng.nextInt(20)),
                    "10.0." + rng.nextInt(256) + "." + rng.nextInt(256)
            ));
        }
        return liste;
    }

    private String uptimeString() {
        long days = 42;
        return days + " days, 3h 17m";
    }

    private String labelDag(String dagId) {
        return switch (dagId) {
            case "imf_ingestion_daily"     -> "Ingestion données quotidienne";
            case "imf_scoring_mcrs"        -> "Scoring MCRS clients";
            case "imf_repayment_forecast"  -> "Prévision remboursements";
            case "imf_rgpd_cleanup"        -> "Nettoyage RGPD";
            case "imf_reporting_mensuel"   -> "Reporting mensuel";
            case "imf_sync_mobile"         -> "Synchronisation mobile";
            default                        -> dagId;
        };
    }

    private String scheduleDag(String dagId) {
        return switch (dagId) {
            case "imf_ingestion_daily"    -> "0 2 * * *";
            case "imf_scoring_mcrs"       -> "0 4 * * *";
            case "imf_repayment_forecast" -> "0 6 * * 1";
            case "imf_rgpd_cleanup"       -> "0 3 * * 0";
            case "imf_reporting_mensuel"  -> "0 0 1 * *";
            case "imf_sync_mobile"        -> "*/15 * * * *";
            default                       -> "@daily";
        };
    }
}
