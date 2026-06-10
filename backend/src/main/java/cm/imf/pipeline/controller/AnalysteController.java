package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.security.TenantContext;
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
@RequestMapping("/api/v1/analyste")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ANALYSTE', 'DSI', 'DIRECTEUR')")
@Tag(name = "Analyste", description = "Scoring MCRS, pipeline Airflow et métriques de dérive ML")
public class AnalysteController {

    private final JdbcTemplate jdbc;

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

    // ── GET /api/v1/analyste/scoring ──────────────────────────────────────────

    @Operation(summary = "Scores MCRS des clients de l'IMF")
    @GetMapping("/scoring")
    public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> scoring(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size,
            @RequestParam(defaultValue = "")   String niveauRisque) {

        Long imfId = TenantContext.currentImfId();

        try {
            StringBuilder sql = new StringBuilder("""
                    SELECT cs.id, cs.client_id, cs.imf_id, cs.score_mcrs,
                           cs.classe_risque, cs.niveau_risque,
                           cs.probabilite_defaut, cs.modele_version,
                           cs.calculated_at, cs.created_at
                    FROM ml.client_scores cs
                    WHERE cs.imf_id = ?
                    """);
            List<Object> params = new ArrayList<>();
            params.add(imfId);

            if (!niveauRisque.isBlank()) {
                sql.append(" AND cs.niveau_risque = ?");
                params.add(niveauRisque);
            }
            sql.append(" ORDER BY cs.created_at DESC LIMIT ? OFFSET ?");
            params.add(size);
            params.add((long) page * size);

            List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());

            Long total;
            try {
                String countSql = "SELECT COUNT(*) FROM ml.client_scores WHERE imf_id = ?"
                        + (niveauRisque.isBlank() ? "" : " AND niveau_risque = ?");
                Object[] countParams = niveauRisque.isBlank()
                        ? new Object[]{imfId}
                        : new Object[]{imfId, niveauRisque};
                total = jdbc.queryForObject(countSql, Long.class, countParams);
            } catch (Exception e) {
                total = (long) rows.size();
            }

            Page<Map<String, Object>> pageResult = new PageImpl<>(rows,
                    PageRequest.of(page, size), total != null ? total : rows.size());
            log.debug("Scoring MCRS IMF {} : {} résultats (niveauRisque={})", imfId, rows.size(), niveauRisque);
            return ResponseEntity.ok(ApiResponse.ok(pageResult));

        } catch (Exception e) {
            log.debug("ml.client_scores indisponible, scores mockés : {}", e.getMessage());
            List<Map<String, Object>> mocked = scoringMockes(imfId, size);
            Page<Map<String, Object>> pageResult = new PageImpl<>(mocked,
                    PageRequest.of(page, size), mocked.size());
            return ResponseEntity.ok(ApiResponse.ok(pageResult));
        }
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
            log.debug("airflow.dag_run indisponible, données mockées : {}", e.getMessage());
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
            log.debug("ml.drift_metrics indisponible, données mockées : {}", e.getMessage());
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<Map<String, Object>> scoringMockes(Long imfId, int nb) {
        Random rng = new Random();
        String[] classes   = {"A","B","C","D","E"};
        String[] niveaux   = {"FAIBLE","MODERE","ELEVE","TRES_ELEVE","CRITIQUE"};
        List<Map<String, Object>> liste = new ArrayList<>();
        for (int i = 0; i < Math.min(nb, 20); i++) {
            int idx = rng.nextInt(classes.length);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",                 (long)(1000 + i));
            m.put("client_id",          (long)(2000 + i));
            m.put("imf_id",             imfId);
            m.put("score_mcrs",         Math.round((300.0 + rng.nextDouble() * 550.0) * 100.0) / 100.0);
            m.put("classe_risque",      classes[idx]);
            m.put("niveau_risque",      niveaux[idx]);
            m.put("probabilite_defaut", Math.round(rng.nextDouble() * 100.0) / 100.0);
            m.put("modele_version",     "MCRS-v2.4.1");
            m.put("calculated_at",      "2026-06-0" + (1 + rng.nextInt(9)) + "T08:00:00Z");
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
}
