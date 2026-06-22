package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentDashboardController {

    private final JdbcTemplate jdbc;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard(
            @AuthenticationPrincipal User user) {

        Long imfId = TenantContext.currentImfId();
        Map<String, Object> result = new LinkedHashMap<>();

        // ── Collectes du jour ─────────────────────────────────────────────────
        long collecteJour  = 0;
        long collectesCount = 0;
        try {
            Map<String, Object> col = jdbc.queryForMap("""
                SELECT COALESCE(SUM(montant_collecte), 0) AS montant,
                       COUNT(*) AS nb
                FROM app.collectes_epargne
                WHERE imf_id = ?
                  AND agent_id = (SELECT id FROM app.utilisateurs WHERE email = ? LIMIT 1)
                  AND DATE(date_collecte) = CURRENT_DATE
                """, imfId, user.getEmail());
            collecteJour  = col.get("montant") instanceof Number n ? n.longValue() : 0L;
            collectesCount = col.get("nb") instanceof Number n ? n.longValue() : 0L;
        } catch (Exception e) {
            log.debug("collectes agent dashboard : {}", e.getMessage());
            // Fallback : collectes du jour de l'IMF
            try {
                Map<String, Object> col = jdbc.queryForMap("""
                    SELECT COALESCE(SUM(montant_collecte), 0) AS montant, COUNT(*) AS nb
                    FROM app.collectes_epargne
                    WHERE imf_id = ? AND DATE(date_collecte) = CURRENT_DATE
                    """, imfId);
                collecteJour   = col.get("montant") instanceof Number n ? n.longValue() : 0L;
                collectesCount = col.get("nb") instanceof Number n ? n.longValue() : 0L;
            } catch (Exception ignored) {}
        }

        // ── Clients dans la zone de l'agent ──────────────────────────────────
        long clientsTotal   = 0;
        long clientsVisites = 0;
        try {
            clientsTotal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app.clients_informels WHERE imf_id = ?",
                Long.class, imfId);
            // Clients avec collecte aujourd'hui = "visités"
            clientsVisites = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT client_id_externe)
                FROM app.collectes_epargne
                WHERE imf_id = ? AND DATE(date_collecte) = CURRENT_DATE
                """, Long.class, imfId);
        } catch (Exception e) {
            log.debug("clients agent dashboard : {}", e.getMessage());
        }

        // ── Objectif journalier (moyenne des 30 derniers jours / 30) ─────────
        long objectifJour = 50_000L;
        try {
            Long moy = jdbc.queryForObject("""
                SELECT COALESCE(SUM(montant_collecte) / NULLIF(COUNT(DISTINCT DATE(date_collecte)), 0), 50000)
                FROM app.collectes_epargne
                WHERE imf_id = ?
                  AND date_collecte >= NOW() - INTERVAL '30 days'
                """, Long.class, imfId);
            if (moy != null && moy > 0) objectifJour = moy;
        } catch (Exception ignored) {}

        // ── Alertes clients pour l'agent ──────────────────────────────────────
        List<Map<String, Object>> alertesClients = new ArrayList<>();
        try {
            alertesClients = jdbc.queryForList("""
                SELECT ap.client_id_externe AS clientId,
                       COALESCE(ci.nom_complet, ap.client_id_externe) AS nom,
                       ap.urgence AS severite,
                       ap.titre   AS message
                FROM ml.alertes_predictives ap
                LEFT JOIN app.clients_informels ci
                       ON ci.client_id_externe = ap.client_id_externe
                      AND ci.imf_id = ap.imf_id
                WHERE ap.imf_id = ? AND ap.statut = 'ACTIVE'
                ORDER BY ap.urgence DESC, ap.created_at DESC
                LIMIT 5
                """, imfId);
        } catch (Exception e) {
            log.debug("alertes agent dashboard : {}", e.getMessage());
        }

        result.put("objectifJour",    objectifJour);
        result.put("collecteJour",    collecteJour);
        result.put("collectesCount",  collectesCount);
        result.put("clientsVisites",  clientsVisites);
        result.put("clientsTotal",    clientsTotal);
        result.put("synchronise",     true);
        result.put("alertesClients",  alertesClients);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
