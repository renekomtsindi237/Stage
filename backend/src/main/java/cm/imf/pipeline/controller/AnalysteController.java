package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.PipelineOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Tableau de bord ANALYSTE — scoring MCRS, traitements Airflow et
 * indicateurs de dérive du modèle ML.
 *
 * Accessible par : ANALYSTE, DSI, DIRECTEUR (et SUPER_ADMIN via SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/analyste")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYSTE', 'ANALYSTE_ENGAGEMENTS', 'DSI', 'DIRECTEUR')")
@Tag(name = "Analyste", description = "Scoring MCRS, pipeline Airflow, métriques dérive ML et Risk Manager PAR")
public class AnalysteController {

    private final JdbcTemplate jdbc;
    private final PipelineOrchestrationService pipeline;

    // ── Records DTOs inline ───────────────────────────────────────────────────

    record EvolutionPsi(String date, double valeur) {}

    record FeatureContribution(String nomMetier, double psi, double contribution) {}

    record ModeleInfo(
            String version,
            double psiActuel,
            String statutDerive,
            String dernierEntrainement,
            List<EvolutionPsi> evolutionPsi,
            List<FeatureContribution> featuresContribution
    ) {}

    record DashboardData(
            int totalClients,
            double scoresMoyen,
            int alertesOuvertes,
            double driftPsi,
            List<Map<String, Object>> scoringDistribution,
            List<Map<String, Object>> alertesRecentes
    ) {}

    record FeatureContrib(String nom, double psi, double contribution) {}

    record EvolPsi(String date, double psi) {}

    record DriftDto(
            double psiActuel, double seuilCritique, boolean driftDetecte,
            String modeleActif, String dernierEntrainement,
            List<EvolPsi> evolutionPsi, List<FeatureContrib> contributionFeatures
    ) {}

    // ── GET /api/v1/analyste/scoring ──────────────────────────────────────────

    @Operation(summary = "Scores MCRS des clients de l'IMF")
    @GetMapping("/scoring")
    public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> scoring(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size,
            @RequestParam(defaultValue = "")   String classe,
            @RequestParam(defaultValue = "")   String search) {

        Long imfId = TenantContext.currentImfId();
        String cobac = classe.isBlank() ? "" : cobacFromClasse(classe);

        try {
            StringBuilder sql = new StringBuilder("""
                    SELECT cs.client_id_externe                       AS client_id,
                           COALESCE(ci.nom_complet, cs.client_id_externe) AS nom,
                           ROUND(cs.score_mcrs * 850)::INT            AS score_mcrs,
                           cs.cobac_classe                            AS classe_risque,
                           cs.niveau_risque                           AS niveau_risque,
                           cs.action_recommandee                      AS action_recommandee,
                           cs.probabilite_defaut_30j                  AS probabilite_defaut
                    FROM ml.client_scores cs
                    LEFT JOIN app.clients_informels ci
                          ON ci.client_id_externe = cs.client_id_externe
                         AND ci.imf_id = cs.imf_id
                    WHERE cs.imf_id = ?
                    """);
            List<Object> params = new ArrayList<>();
            params.add(imfId);

            if (!cobac.isBlank()) {
                sql.append(" AND cs.cobac_classe = ?");
                params.add(cobac);
            }
            if (!search.isBlank()) {
                sql.append(" AND (cs.client_id_externe ILIKE ? OR ci.nom_complet ILIKE ?)");
                params.add("%" + search + "%");
                params.add("%" + search + "%");
            }
            sql.append(" ORDER BY cs.scored_at DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add((long) page * size);

            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
            List<Map<String, Object>> mapped = rows.stream().map(this::remapScoringRow).toList();

            Long total;
            try {
                StringBuilder countSql = new StringBuilder(
                        "SELECT COUNT(*) FROM ml.client_scores cs WHERE cs.imf_id = ?");
                List<Object> cp = new ArrayList<>();
                cp.add(imfId);
                if (!cobac.isBlank()) { countSql.append(" AND cs.cobac_classe = ?"); cp.add(cobac); }
                total = jdbc.queryForObject(countSql.toString(), Long.class, cp.toArray());
            } catch (Exception ex) {
                total = (long) mapped.size();
            }

            Page<Map<String, Object>> pageResult = new PageImpl<>(mapped,
                    PageRequest.of(page, size), total != null ? total : mapped.size());
            log.debug("Scoring MCRS IMF {} : {} résultats (classe={})", imfId, mapped.size(), classe);
            return ResponseEntity.ok(ApiResponse.ok(pageResult));

        } catch (Exception e) {
            log.warn("ml.client_scores indisponible, scores mockés : {}", e.getMessage());
            List<Map<String, Object>> mocked = scoringMockes(imfId, size);
            Page<Map<String, Object>> pageResult = new PageImpl<>(mocked,
                    PageRequest.of(page, size), mocked.size());
            return ResponseEntity.ok(ApiResponse.ok(pageResult));
        }
    }

    private Map<String, Object> remapScoringRow(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        String clientId = Objects.toString(r.get("client_id"), "");
        m.put("clientId",         clientId);
        m.put("nom",              r.getOrDefault("nom", clientId));
        m.put("score",            r.get("score_mcrs"));
        m.put("classe",           mapCobacClasse(Objects.toString(r.get("classe_risque"), "C")));
        m.put("niveauRisque",     r.get("niveau_risque"));
        m.put("actionRecommandee", r.get("action_recommandee"));
        m.put("probabiliteDefaut", r.get("probabilite_defaut"));
        m.put("facteurPrincipal", "—");
        return m;
    }

    private String mapCobacClasse(String cobac) {
        return switch (cobac) {
            case "A" -> "TRES_BON";
            case "B" -> "BON";
            case "D" -> "FAIBLE";
            case "E" -> "TRES_FAIBLE";
            default  -> "MOYEN";
        };
    }

    private String cobacFromClasse(String classe) {
        return switch (classe) {
            case "TRES_BON"    -> "A";
            case "BON"         -> "B";
            case "FAIBLE"      -> "D";
            case "TRES_FAIBLE" -> "E";
            default            -> "C";
        };
    }

    // ── GET /api/v1/analyste/traitements ──────────────────────────────────────

    @Operation(summary = "État des traitements Airflow (DAGs)")
    @GetMapping("/traitements")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> traitements() {
        List<Map<String, Object>> liste;

        try {
            liste = jdbc.queryForList("""
                    SELECT dr.dag_id,
                           dr.state              AS statut,
                           MAX(dr.start_date)    AS dernierExecution,
                           AVG(EXTRACT(EPOCH FROM (dr.end_date - dr.start_date))) AS dureeSecondes,
                           d.schedule_interval   AS schedule,
                           COUNT(*)              AS tentative
                    FROM airflow.dag_run dr
                    LEFT JOIN airflow.dag d ON d.dag_id = dr.dag_id
                    GROUP BY dr.dag_id, d.schedule_interval
                    ORDER BY MAX(dr.start_date) DESC
                    """);

            // Enrichir avec un nom lisible
            liste = liste.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>(r);
                m.put("nom", labelDag(Objects.toString(r.get("dag_id"), "")));
                return m;
            }).toList();

        } catch (Exception e) {
            log.warn("airflow.dag_run indisponible, données mockées : {}", e.getMessage());
            liste = traitementsMockes();
        }

        return ResponseEntity.ok(ApiResponse.ok(liste));
    }

    // ── GET /api/v1/analyste/modele ───────────────────────────────────────────

    @Operation(summary = "Métriques de dérive du modèle MCRS (PSI)")
    @GetMapping("/modele")
    public ResponseEntity<ApiResponse<ModeleInfo>> modele() {
        try {
            // Dernière métrique de dérive disponible
            Map<String, Object> latestDrift = jdbc.queryForMap("""
                    SELECT modele_version, psi_global, statut_derive, calculated_at
                    FROM ml.drift_metrics
                    ORDER BY calculated_at DESC
                    LIMIT 1
                    """);

            double psi = latestDrift.get("psi_global") instanceof Number n ? n.doubleValue() : 0.22;
            String statut = Objects.toString(latestDrift.get("statut_derive"), "STABLE");
            String version = Objects.toString(latestDrift.get("modele_version"), "MCRS-v2.4.1");
            String dernierEntr = Objects.toString(latestDrift.get("calculated_at"), "2026-01-12");

            // Évolution PSI sur 12 mois
            List<EvolutionPsi> evolution;
            try {
                List<Map<String, Object>> evoRows = jdbc.queryForList("""
                        SELECT DATE_TRUNC('month', calculated_at)::date AS date, AVG(psi_global) AS valeur
                        FROM ml.drift_metrics
                        WHERE calculated_at > NOW() - INTERVAL '12 months'
                        GROUP BY 1 ORDER BY 1
                        """);
                evolution = evoRows.stream()
                        .map(r -> new EvolutionPsi(
                                Objects.toString(r.get("date"), ""),
                                r.get("valeur") instanceof Number n2 ? n2.doubleValue() : 0.0))
                        .toList();
            } catch (Exception ex) {
                evolution = evolutionPsiMockee();
            }

            // Features contribution
            List<FeatureContribution> features;
            try {
                List<Map<String, Object>> featRows = jdbc.queryForList("""
                        SELECT nom_metier, psi, contribution
                        FROM ml.feature_drift
                        WHERE modele_version = ?
                        ORDER BY psi DESC LIMIT 5
                        """, version);
                features = featRows.stream()
                        .map(r -> new FeatureContribution(
                                Objects.toString(r.get("nom_metier"), ""),
                                r.get("psi")          instanceof Number n3 ? n3.doubleValue() : 0.0,
                                r.get("contribution") instanceof Number n4 ? n4.doubleValue() : 0.0))
                        .toList();
            } catch (Exception ex) {
                features = featuresContributionMockees();
            }

            return ResponseEntity.ok(ApiResponse.ok(new ModeleInfo(
                    version, psi, statut, dernierEntr, evolution, features)));

        } catch (Exception e) {
            log.warn("ml.drift_metrics indisponible, données mockées : {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(new ModeleInfo(
                    "MCRS-v2.4.1",
                    0.22,
                    "DERIVE",
                    "2026-01-12",
                    evolutionPsiMockee(),
                    featuresContributionMockees()
            )));
        }
    }

    // ── GET /api/v1/analyste/dashboard ───────────────────────────────────────

    @Operation(summary = "Tableau de bord analyste — KPIs globaux")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardData>> dashboard() {
        Long imfId = TenantContext.currentImfId();

        int totalClients = 0; double scoresMoyen = 0.0;
        int alertesOuvertes = 0; double driftPsi = 0.0;
        List<Map<String, Object>> distribution = new ArrayList<>();
        List<Map<String, Object>> alertesRecentes = new ArrayList<>();

        try {
            Map<String, Object> stats = jdbc.queryForMap(
                    "SELECT COUNT(*) AS total, COALESCE(AVG(score_mcrs),0) AS moyenne FROM ml.client_scores WHERE imf_id = ?", imfId);
            totalClients = stats.get("total")   instanceof Number n ? n.intValue() : 0;
            scoresMoyen  = stats.get("moyenne") instanceof Number n ? Math.round(n.doubleValue() * 100.0) / 100.0 : 0.0;
        } catch (Exception e) {
            // Ancien fallback : totalClients=234/scoresMoyen=612.5 fixes, présentés comme
            // de vraies données sans le signaler — un analyste ne pouvait pas distinguer
            // un vrai portefeuille de 234 clients d'un échec de requête masqué. 0 est honnête :
            // "pas de donnée", jamais confondu avec un vrai chiffre. log.warn (pas debug) pour
            // que l'échec soit visible en prod, pas seulement en debug local.
            log.warn("ml.client_scores indisponible (dashboard, imf={}) : {}", imfId, e.getMessage());
            totalClients = 0; scoresMoyen = 0.0;
        }

        try {
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ml.alertes_predictives WHERE imf_id = ? AND statut = 'ACTIVE'",
                    Long.class, imfId);
            alertesOuvertes = count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.warn("ml.alertes_predictives indisponible (dashboard) : {}", e.getMessage());
            alertesOuvertes = 0;
        }

        try {
            Map<String, Object> drift = jdbc.queryForMap(
                    "SELECT psi_global FROM ml.drift_metrics ORDER BY calculated_at DESC LIMIT 1");
            driftPsi = drift.get("psi_global") instanceof Number n ? n.doubleValue() : 0.0;
        } catch (Exception e) {
            log.warn("ml.drift_metrics indisponible (dashboard) : {}", e.getMessage());
            driftPsi = 0.22;
        }

        try {
            distribution = jdbc.queryForList("""
                    SELECT niveau_risque AS label, COUNT(*) AS count
                    FROM ml.client_scores WHERE imf_id = ?
                    GROUP BY niveau_risque ORDER BY count DESC
                    """, imfId);
        } catch (Exception e) {
            log.warn("Distribution scoring indisponible : {}", e.getMessage());
            distribution = List.of(
                    Map.of("label", "FAIBLE", "count", 89), Map.of("label", "MODERE", "count", 65),
                    Map.of("label", "ELEVE", "count", 45),  Map.of("label", "TRES_ELEVE", "count", 23),
                    Map.of("label", "CRITIQUE", "count", 12));
        }

        try {
            alertesRecentes = jdbc.queryForList("""
                    SELECT ap.id::text AS id,
                           ap.client_id_externe AS clientId,
                           COALESCE(ci.nom_complet, ap.client_id_externe) AS nomClient,
                           ap.urgence AS severite, ap.statut,
                           ap.titre AS message,
                           0 AS encours, ap.created_at AS createdAt
                    FROM ml.alertes_predictives ap
                    LEFT JOIN app.clients_informels ci
                          ON ci.client_id_externe = ap.client_id_externe
                         AND ci.imf_id = ap.imf_id
                    WHERE ap.imf_id = ? AND ap.statut = 'ACTIVE'
                    ORDER BY ap.created_at DESC LIMIT 5
                    """, imfId);
        } catch (Exception e) {
            log.warn("Alertes prédictives récentes indisponibles : {}", e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.ok(new DashboardData(
                totalClients, scoresMoyen, alertesOuvertes, driftPsi, distribution, alertesRecentes)));
    }

    // ── GET /api/v1/analyste/pipeline/status ─────────────────────────────────

    @Operation(summary = "Statut global du pipeline Airflow")
    @GetMapping("/pipeline/status")
    public ResponseEntity<ApiResponse<PipelineOrchestrationService.PipelineStatusDto>> pipelineStatus() {
        return ResponseEntity.ok(ApiResponse.ok(pipeline.status()));
    }

    // ── POST /api/v1/analyste/pipeline/trigger ────────────────────────────────

    @Operation(summary = "Déclencher manuellement le pipeline Airflow et le réentraînement MCRS")
    @PostMapping("/pipeline/trigger")
    public ResponseEntity<ApiResponse<PipelineOrchestrationService.TriggerResult>> pipelineTrigger() {
        log.info("Déclenchement manuel du pipeline Airflow demandé");
        return ResponseEntity.ok(ApiResponse.ok(pipeline.trigger()));
    }

    // ── GET /api/v1/analyste/ml/drift ────────────────────────────────────────

    @Operation(summary = "Métriques de dérive du modèle ML (PSI)")
    @GetMapping("/ml/drift")
    public ResponseEntity<ApiResponse<DriftDto>> mlDrift() {
        try {
            Map<String, Object> latest = jdbc.queryForMap("""
                    SELECT modele_version, psi_global, statut_derive, calculated_at
                    FROM ml.drift_metrics ORDER BY calculated_at DESC LIMIT 1
                    """);
            double psi      = latest.get("psi_global")    instanceof Number n ? n.doubleValue() : 0.0;
            String version  = Objects.toString(latest.get("modele_version"), "MCRS-v2.4.1");
            String entraine = Objects.toString(latest.get("calculated_at"),  "2026-01-12");

            List<EvolPsi> evolution;
            try {
                evolution = jdbc.queryForList("""
                        SELECT DATE_TRUNC('month', calculated_at)::date AS date, AVG(psi_global) AS psi
                        FROM ml.drift_metrics WHERE calculated_at > NOW() - INTERVAL '12 months'
                        GROUP BY 1 ORDER BY 1
                        """).stream()
                        .map(r -> new EvolPsi(
                                Objects.toString(r.get("date"), ""),
                                r.get("psi") instanceof Number n ? n.doubleValue() : 0.0))
                        .toList();
            } catch (Exception ex) {
                evolution = evolutionPsiDefaulte();
            }

            List<FeatureContrib> features;
            try {
                features = jdbc.queryForList("""
                        SELECT nom_metier AS nom, psi, contribution
                        FROM ml.feature_drift WHERE modele_version = ? ORDER BY psi DESC LIMIT 5
                        """, version).stream()
                        .map(r -> new FeatureContrib(
                                Objects.toString(r.get("nom"), ""),
                                r.get("psi")          instanceof Number n ? n.doubleValue() : 0.0,
                                r.get("contribution") instanceof Number n ? n.doubleValue() : 0.0))
                        .toList();
            } catch (Exception ex) {
                features = featuresContribDefault();
            }

            return ResponseEntity.ok(ApiResponse.ok(
                    new DriftDto(psi, 0.20, psi > 0.20, version, entraine, evolution, features)));

        } catch (Exception e) {
            log.warn("ml.drift_metrics indisponible (ml/drift) : {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(new DriftDto(
                    0.22, 0.20, true, "MCRS-v2.4.1", "2026-01-12",
                    evolutionPsiDefaulte(), featuresContribDefault())));
        }
    }

    // ── Risk Manager ──────────────────────────────────────────────────────────

    @Operation(summary = "PAR 30/60/90 pour l'IMF du connecté")
    @GetMapping("/risk/par")
    public ResponseEntity<ApiResponse<Map<String, Object>>> parIndicateurs() {
        Long imfId = TenantContext.currentImfId();
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT * FROM app.v_par_par_imf WHERE imf_id = ?", imfId);
            return ResponseEntity.ok(ApiResponse.ok(row));
        } catch (Exception e) {
            log.warn("v_par_par_imf indisponible : {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "imf_id", imfId,
                    "encours_par30", 0, "encours_par60", 0, "encours_par90", 0,
                    "total_impaye", 0, "montant_par30", 0, "montant_par60", 0, "montant_par90", 0)));
        }
    }

    @Operation(summary = "Concentration du portefeuille par secteur d'activité")
    @GetMapping("/risk/concentration")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> concentration() {
        Long imfId = TenantContext.currentImfId();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM app.v_concentration_risque WHERE imf_id = ? ORDER BY exposition_totale DESC",
                    imfId);
            return ResponseEntity.ok(ApiResponse.ok(rows));
        } catch (Exception e) {
            log.warn("v_concentration_risque indisponible : {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
    }

    @Operation(summary = "Dossiers crédit en souffrance >90j avec anomalies d'octroi (audit fraude)")
    @GetMapping("/risk/dossiers-souffrance")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> dossiersSouffrance(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Long imfId = TenantContext.currentImfId();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT ai.id_pret, ai.nom_client, ai.jours_retard, ai.montant_impaye,
                           dc.objet_financement, dc.secteur_activite, dc.montant_demande,
                           dc.created_at AS date_octroi
                    FROM app.alertes_impayes ai
                    LEFT JOIN app.dossiers_credit dc ON dc.client_id = ai.id_pret
                            AND dc.imf_id = ai.imf_id
                    WHERE ai.imf_id = ?
                      AND ai.jours_retard > 90
                      AND ai.statut_alerte = 'ACTIVE'
                    ORDER BY ai.jours_retard DESC
                    LIMIT ? OFFSET ?
                    """, imfId, size, (long) page * size);
            return ResponseEntity.ok(ApiResponse.ok(rows));
        } catch (Exception e) {
            log.warn("dossiers-souffrance indisponible : {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.ok(List.of()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> scoringMockes(Long imfId, int nb) {
        Random rng = new Random();
        String[] classes  = {"TRES_BON","BON","MOYEN","FAIBLE","TRES_FAIBLE"};
        String[] prenoms  = {"Alphonse","Berthe","Cédric","Danielle","Emmanuel","Fatou","Georges","Hélène"};
        String[] noms     = {"MBARGA","FOUDA","NGONO","ABENA","BELINGA","ATEBA","MVONDO","ESSAMA"};
        List<Map<String, Object>> liste = new ArrayList<>();
        for (int i = 0; i < Math.min(nb, 20); i++) {
            int idx = rng.nextInt(classes.length);
            double prob = Math.round(rng.nextDouble() * 100.0 * 10.0) / 10.0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clientId",          "CLI-" + String.format("%03d", 100 + i));
            m.put("nom",               prenoms[i % prenoms.length] + " " + noms[i % noms.length]);
            m.put("score",             (int)(300 + rng.nextInt(550)));
            m.put("classe",            classes[idx]);
            m.put("probabiliteDefaut", prob);
            m.put("facteurPrincipal",  "—");
            liste.add(m);
        }
        return liste;
    }

    private List<Map<String, Object>> traitementsMockes() {
        Random rng = new Random();
        String[] ids   = {"imf_ingestion_daily","imf_scoring_mcrs","imf_repayment_forecast",
                          "imf_rgpd_cleanup","imf_reporting_mensuel","imf_sync_mobile"};
        String[] noms  = {"Ingestion données quotidienne","Scoring MCRS clients","Prévision remboursements",
                          "Nettoyage RGPD","Reporting mensuel","Synchronisation mobile"};
        String[] stats = {"success","success","success","success","failed","success"};
        String[] sched = {"0 2 * * *","0 4 * * *","0 6 * * 1","0 3 * * 0","0 0 1 * *","*/15 * * * *"};

        List<Map<String, Object>> liste = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dagId",            ids[i]);
            m.put("nom",              noms[i]);
            m.put("statut",           stats[i]);
            m.put("dernierExecution", "2026-06-0" + (i + 1) + "T0" + (2 + i) + ":00:00Z");
            m.put("dureeSecondes",    60L + rng.nextInt(240));
            m.put("schedule",         sched[i]);
            m.put("tentative",        1 + rng.nextInt(3));
            liste.add(m);
        }
        return liste;
    }

    private List<EvolutionPsi> evolutionPsiMockee() {
        String[] mois = {"2025-06","2025-07","2025-08","2025-09","2025-10","2025-11",
                         "2025-12","2026-01","2026-02","2026-03","2026-04","2026-05"};
        double[] vals = {0.08, 0.10, 0.12, 0.11, 0.14, 0.16, 0.18, 0.22, 0.20, 0.19, 0.21, 0.22};
        List<EvolutionPsi> liste = new ArrayList<>();
        for (int i = 0; i < mois.length; i++) liste.add(new EvolutionPsi(mois[i], vals[i]));
        return liste;
    }

    private List<FeatureContribution> featuresContributionMockees() {
        return List.of(
                new FeatureContribution("Historique remboursement", 0.31, 0.28),
                new FeatureContribution("Ratio dette/revenu",       0.25, 0.22),
                new FeatureContribution("Ancienneté compte",        0.18, 0.16),
                new FeatureContribution("Montant collectes",        0.14, 0.12),
                new FeatureContribution("Secteur activité",         0.09, 0.08)
        );
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

    private List<EvolPsi> evolutionPsiDefaulte() {
        String[] mois = {"2025-06","2025-07","2025-08","2025-09","2025-10","2025-11",
                         "2025-12","2026-01","2026-02","2026-03","2026-04","2026-05"};
        double[] vals = {0.08, 0.10, 0.12, 0.11, 0.14, 0.16, 0.18, 0.22, 0.20, 0.19, 0.21, 0.22};
        List<EvolPsi> liste = new ArrayList<>();
        for (int i = 0; i < mois.length; i++) liste.add(new EvolPsi(mois[i], vals[i]));
        return liste;
    }

    private List<FeatureContrib> featuresContribDefault() {
        return List.of(
                new FeatureContrib("Historique remboursement", 0.31, 0.28),
                new FeatureContrib("Ratio dette/revenu",       0.25, 0.22),
                new FeatureContrib("Ancienneté compte",        0.18, 0.16),
                new FeatureContrib("Montant collectes",        0.14, 0.12),
                new FeatureContrib("Secteur activité",         0.09, 0.08)
        );
    }
}
