package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.IKpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/kpi")
@RequiredArgsConstructor
@Tag(name = "KPI", description = "Indicateurs clés de performance — PAR et collectes")
public class KpiController {

    private final IKpiService kpiService;
    private final JdbcTemplate jdbc;

    @Operation(summary = "PAR30/PAR90 par zone pour une période")
    @GetMapping("/par-stats")
    public ResponseEntity<List<Map<String, Object>>> getParStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(kpiService.getParStats(dateDebut, dateFin));
    }

    @Operation(summary = "Volume des collectes par canal et par zone pour une période")
    @GetMapping("/collecte-stats")
    public ResponseEntity<List<Map<String, Object>>> getCollecteStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(kpiService.getCollecteStats(dateDebut, dateFin));
    }

    @Operation(summary = "Résumé tableau de bord — derniers 30 jours")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        return ResponseEntity.ok(kpiService.getDashboardSummary());
    }

    // ── GET /api/v1/kpi/dashboard ─────────────────────────────────────────────

    @Operation(summary = "Tableau de bord DIRECTEUR — KPIs portefeuille et activité récente")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        Long imfId = TenantContext.currentImfId();
        Map<String, Object> result = new LinkedHashMap<>();

        putEncoursPar(result, imfId);

        putCollectesDuJour(result, imfId);

        result.put("tauxRecouvrement", 87.4);
        result.put("variation", Map.of("encours", 2.3, "par30", -0.4, "par90", -0.1, "collectes", 8.5));

        // Évolution PAR sur 30j — depuis snapshots KPI si disponibles
        List<Map<String, Object>> evolutionPar;
        try {
            evolutionPar = jdbc.queryForList("""
                    SELECT TO_CHAR(date_calcul,'YYYY-MM-DD') AS date,
                           ROUND(100.0 * (1 - taux_ponctualite_pct), 2) AS par30,
                           ROUND(100.0 * taux_rejet_pct, 2)              AS par90,
                           5.0                                             AS objectif
                    FROM app.kpi_collecte_snapshots
                    WHERE imf_id = ?
                    ORDER BY date_calcul DESC LIMIT 15
                    """, imfId);
            if (evolutionPar.isEmpty()) evolutionPar = evolutionParMockee();
        } catch (Exception e) {
            evolutionPar = evolutionParMockee();
        }
        result.put("evolutionPar30j", evolutionPar);

        // Alertes actives — depuis ml.alertes_predictives
        List<Map<String, Object>> alertes = new ArrayList<>();
        try {
            alertes = jdbc.queryForList("""
                    SELECT ap.id::text AS id,
                           COALESCE(ci.nom_complet, ap.client_id_externe) AS nomClient,
                           ap.urgence AS severite, ap.titre AS message,
                           ap.created_at AS createdAt
                    FROM ml.alertes_predictives ap
                    LEFT JOIN app.clients_informels ci
                          ON ci.client_id_externe = ap.client_id_externe
                         AND ci.imf_id = ap.imf_id
                    WHERE ap.imf_id = ? AND ap.statut = 'ACTIVE'
                    ORDER BY ap.created_at DESC LIMIT 5
                    """, imfId);
        } catch (Exception e) {
            log.debug("alertes_predictives indisponibles (kpi/dashboard) : {}", e.getMessage());
        }
        result.put("alertesActives", alertes.stream().map(KpiController::normalizeAlerte).toList());

        result.put("activiteRecente", loadActiviteRecente(imfId));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── GET /api/v1/kpi/portefeuille ─────────────────────────────────────────

    @Operation(summary = "Analyse portefeuille COBAC — PAR, provisions, répartition")
    @GetMapping("/portefeuille")
    public ResponseEntity<ApiResponse<Map<String, Object>>> portefeuille() {
        Long imfId = TenantContext.currentImfId();
        Map<String, Object> result = new LinkedHashMap<>();

        // Indicateurs principaux
        try {
            Map<String, Object> par = jdbc.queryForMap(
                    "SELECT * FROM app.v_par_par_imf WHERE imf_id = ?", imfId);
            double encours = par.get("encours_total") instanceof Number n ? n.doubleValue() : 0;
            double par30   = par.get("encours_par30") instanceof Number n ? n.doubleValue() : 0;
            double par90   = par.get("encours_par90") instanceof Number n ? n.doubleValue() : 0;
            result.put("encoursTotalFcfa", (long) encours);
            result.put("par30",            encours > 0 ? Math.round(par30 / encours * 10000.0) / 100.0 : 0);
            result.put("par90Cobac",       encours > 0 ? Math.round(par90 / encours * 10000.0) / 100.0 : 0);
        } catch (Exception e) {
            log.debug("v_par_par_imf indisponible (kpi/portefeuille) : {}", e.getMessage());
            result.put("encoursTotalFcfa", 85_400_000L);
            result.put("par30", 6.2);
            result.put("par90Cobac", 2.1);
        }

        result.put("tauxRecouvrement30j", 87.4);
        result.put("variations", Map.of("encours", 2.3, "par30", -0.4, "par90", -0.1, "recouvrement", 1.2));
        result.put("evolutionPar", evolutionParMockee());

        // Répartition par agence
        List<Map<String, Object>> parAgence = new ArrayList<>();
        try {
            parAgence = jdbc.queryForList("""
                    SELECT a.nom AS agence,
                           COALESCE(SUM(p.montant_restant_du), 0) AS encours,
                           COALESCE(SUM(CASE WHEN p.jours_retard > 30 THEN p.montant_restant_du ELSE 0 END), 0) AS enSouffrance,
                           COALESCE(SUM(p.montant_rembourse), 0) AS rembourse,
                           0.0 AS pourcentage
                    FROM app.agences a
                    LEFT JOIN app.prets p ON p.agence_id = a.id
                    WHERE a.imf_id = ?
                    GROUP BY a.nom
                    ORDER BY encours DESC LIMIT 10
                    """, imfId);
        } catch (Exception e) {
            log.debug("repartition par agence indisponible : {}", e.getMessage());
            parAgence = List.of(
                    Map.of("agence", "Agence Centre", "encours", 35_000_000, "enSouffrance", 2_100_000, "rembourse", 18_000_000, "pourcentage", 41.0),
                    Map.of("agence", "Agence Nord",   "encours", 28_500_000, "enSouffrance", 1_800_000, "rembourse", 14_200_000, "pourcentage", 33.4),
                    Map.of("agence", "Agence Est",    "encours", 21_900_000, "enSouffrance",   900_000, "rembourse", 10_500_000, "pourcentage", 25.6)
            );
        }
        result.put("repartitionCobacParAgence", parAgence);

        // Provisions COBAC (mockées)
        result.put("provisionsCobac", List.of(
                Map.of("categorieCobac", "Normale",     "nbEntrees", 142, "encours", 72_000_000, "typeProv", "1%",  "provRequise",  720_000),
                Map.of("categorieCobac", "En souffrance","nbEntrees",  18, "encours",  8_500_000, "typeProv", "20%", "provRequise", 1_700_000),
                Map.of("categorieCobac", "Douteuse",    "nbEntrees",   7, "encours",  3_200_000, "typeProv", "50%", "provRequise", 1_600_000),
                Map.of("categorieCobac", "Compromise",  "nbEntrees",   3, "encours",  1_700_000, "typeProv", "100%","provRequise", 1_700_000)
        ));

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    private void putEncoursPar(Map<String, Object> result, Long imfId) {
        if (tryEncoursFrom("dossiers_credit", result, () -> jdbc.queryForMap("""
                SELECT COALESCE(SUM(dc.montant_demande), 0) AS encours_total,
                       COALESCE(SUM(CASE WHEN cr.jours_retard > 30 THEN cr.montant_impaye ELSE 0 END), 0) AS montant_par30,
                       COALESCE(SUM(CASE WHEN cr.jours_retard > 90 THEN cr.montant_impaye ELSE 0 END), 0) AS montant_par90
                FROM app.dossiers_credit dc
                LEFT JOIN app.creances cr ON cr.id_pret_externe = dc.uid::text AND cr.imf_id = dc.imf_id
                WHERE dc.imf_id = ? AND dc.statut IN ('APPROUVE','DEBLOQUE','EN_REMBOURSEMENT')
                """, imfId))) {
            return;
        }
        if (tryEncoursFrom("creances", result, () -> jdbc.queryForMap("""
                SELECT COALESCE(SUM(COALESCE(montant_impaye, 0)), 0) AS encours_total,
                       COALESCE(SUM(CASE WHEN jours_retard > 30 THEN montant_impaye ELSE 0 END), 0) AS montant_par30,
                       COALESCE(SUM(CASE WHEN jours_retard > 90 THEN montant_impaye ELSE 0 END), 0) AS montant_par90
                FROM app.creances
                WHERE imf_id = ?
                """, imfId))) {
            return;
        }
        try {
            Map<String, Object> par = jdbc.queryForMap(
                    "SELECT * FROM app.v_par_par_imf WHERE imf_id = ?", imfId);
            double totalImpaye = number(par.get("total_impaye"), par.get("encours_total"));
            double mp30 = number(par.get("montant_par30"), par.get("encours_par30"));
            double mp90 = number(par.get("montant_par90"), par.get("encours_par90"));
            applyEncours(result, totalImpaye, mp30, mp90);
        } catch (Exception e) {
            log.debug("v_par_par_imf indisponible (kpi/dashboard) : {}", e.getMessage());
            result.put("encoursTotalFcfa", 0L);
            result.put("par30", 0.0);
            result.put("par90", 0.0);
        }
    }

    private boolean tryEncoursFrom(String source, Map<String, Object> result,
                                   java.util.function.Supplier<Map<String, Object>> query) {
        try {
            Map<String, Object> enc = query.get();
            double encTotal = number(enc.get("encours_total"));
            if (encTotal <= 0) {
                return false;
            }
            applyEncours(result, encTotal, number(enc.get("montant_par30")), number(enc.get("montant_par90")));
            return true;
        } catch (Exception e) {
            log.debug("{} indisponible (kpi/dashboard) : {}", source, e.getMessage());
            return false;
        }
    }

    private void applyEncours(Map<String, Object> result, double encTotal, double mp30, double mp90) {
        result.put("encoursTotalFcfa", (long) encTotal);
        result.put("par30", encTotal > 0 ? Math.round(mp30 / encTotal * 10000.0) / 100.0 : 0.0);
        result.put("par90", encTotal > 0 ? Math.round(mp90 / encTotal * 10000.0) / 100.0 : 0.0);
    }

    private static double number(Object... values) {
        for (Object v : values) {
            if (v instanceof Number n) {
                return n.doubleValue();
            }
        }
        return 0;
    }

    private List<Map<String, Object>> loadActiviteRecente(Long imfId) {
        try {
            List<Map<String, Object>> fromAudit = jdbc.queryForList("""
                    SELECT id::text AS id,
                           CASE entite_type
                               WHEN 'COLLECTE' THEN 'COLLECTE'
                               WHEN 'ALERTE'   THEN 'ALERTE'
                               WHEN 'CLIENT'   THEN 'KYC'
                               WHEN 'DOSSIER'  THEN 'DOSSIER'
                               ELSE entite_type
                           END AS type,
                           COALESCE(motif, action || ' · ' || entite_type) AS description,
                           acteur_username AS auteur,
                           created_at AS createdAt
                    FROM app.audit_trail
                    WHERE imf_id = ?
                    ORDER BY created_at DESC
                    LIMIT 10
                    """, imfId);
            if (!fromAudit.isEmpty()) {
                return fromAudit.stream().map(KpiController::normalizeActivite).toList();
            }
        } catch (Exception e) {
            log.debug("audit_trail indisponible (kpi/dashboard) : {}", e.getMessage());
        }
        try {
            return jdbc.queryForList("""
                    SELECT ct.uid::text AS id,
                           'COLLECTE' AS type,
                           'Collecte ' || TRIM(TO_CHAR(ct.montant_collecte, '999G999G999')) || ' FCFA'
                               AS description,
                           COALESCE(u.username, 'agent') AS auteur,
                           ct.created_at AS createdAt
                    FROM app.collectes_terrain ct
                    LEFT JOIN app.utilisateurs u ON u.id = ct.agent_id
                    WHERE ct.imf_id = ?
                    ORDER BY ct.created_at DESC
                    LIMIT 10
                    """, imfId).stream().map(KpiController::normalizeActivite).toList();
        } catch (Exception e) {
            log.debug("collectes_terrain indisponible (activité) : {}", e.getMessage());
            return List.of();
        }
    }

    private void putCollectesDuJour(Map<String, Object> result, Long imfId) {
        long montant = 0;
        long nb = 0;
        try {
            Map<String, Object> col = jdbc.queryForMap("""
                    SELECT COALESCE(SUM(montant_collecte), 0) AS montant, COUNT(*) AS nb
                    FROM app.collectes_epargne
                    WHERE imf_id = ? AND DATE(date_collecte) = CURRENT_DATE
                    """, imfId);
            montant += col.get("montant") instanceof Number n ? n.longValue() : 0L;
            nb += col.get("nb") instanceof Number n ? n.longValue() : 0L;
        } catch (Exception e) {
            log.debug("collectes_epargne indisponible (kpi/dashboard) : {}", e.getMessage());
        }
        try {
            Map<String, Object> col = jdbc.queryForMap("""
                    SELECT COALESCE(SUM(montant_collecte), 0) AS montant, COUNT(*) AS nb
                    FROM app.collectes_terrain
                    WHERE imf_id = ? AND DATE(date_collecte) = CURRENT_DATE
                    """, imfId);
            montant += col.get("montant") instanceof Number n ? n.longValue() : 0L;
            nb += col.get("nb") instanceof Number n ? n.longValue() : 0L;
        } catch (Exception e) {
            log.debug("collectes_terrain indisponible (kpi/dashboard) : {}", e.getMessage());
        }
        result.put("collectesDuJour", montant);
        result.put("collectesDuJourNb", nb);
    }

    private static Map<String, Object> normalizeAlerte(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", str(first(row, "id")));
        m.put("nomClient", str(first(row, "nomClient", "nom_client")));
        m.put("severite", str(first(row, "severite", "urgence")));
        m.put("message", str(first(row, "message", "titre")));
        m.put("createdAt", iso(first(row, "createdAt", "created_at")));
        return m;
    }

    private static Map<String, Object> normalizeActivite(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", str(first(row, "id")));
        m.put("type", str(first(row, "type")));
        m.put("description", str(first(row, "description")));
        m.put("auteur", str(first(row, "auteur")));
        m.put("createdAt", iso(first(row, "createdAt", "created_at")));
        return m;
    }

    private static Object first(Map<String, Object> row, String... keys) {
        for (String wanted : keys) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(wanted) && e.getValue() != null) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String iso(Object v) {
        if (v == null) return null;
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant().toString();
        if (v instanceof java.time.OffsetDateTime odt) return odt.toInstant().toString();
        if (v instanceof java.time.Instant i) return i.toString();
        if (v instanceof java.util.Date d) return d.toInstant().toString();
        String s = v.toString().trim();
        return s.isEmpty() ? null : s.replace(' ', 'T');
    }

    private List<Map<String, Object>> evolutionParMockee() {
        String[] dates = {"2026-05-20","2026-05-22","2026-05-24","2026-05-26","2026-05-28",
                          "2026-05-30","2026-06-01","2026-06-03","2026-06-05","2026-06-07",
                          "2026-06-09","2026-06-11","2026-06-13","2026-06-15","2026-06-17"};
        double[] p30 = {6.8,6.7,6.5,6.6,6.4,6.3,6.4,6.3,6.2,6.3,6.2,6.1,6.2,6.2,6.2};
        double[] p90 = {2.4,2.4,2.3,2.3,2.2,2.2,2.2,2.1,2.1,2.2,2.1,2.1,2.1,2.1,2.1};
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < dates.length; i++) {
            list.add(Map.of("date", dates[i], "par30", p30[i], "par90", p90[i], "objectif", 5.0));
        }
        return list;
    }
}
