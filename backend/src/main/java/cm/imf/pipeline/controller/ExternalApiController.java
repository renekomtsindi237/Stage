package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.CollecteRequest;
import cm.imf.pipeline.dto.response.*;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.service.ICollecteService;
import cm.imf.pipeline.service.ICreanceService;
import cm.imf.pipeline.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API externe — endpoints pour intégrations CBS et BluCash.
 * Authentification : X-Api-Key header uniquement (pas de JWT).
 * Toutes les routes sont sous /api/v1/external/**.
 */
@RestController
@RequestMapping("/external")
@RequiredArgsConstructor
@Tag(name = "External API", description = "Endpoints pour intégrations CBS et BluCash — authentification par X-Api-Key")
@PreAuthorize("hasRole('API_CLIENT')")
@SecurityRequirement(name = "ApiKeyAuth")
public class ExternalApiController {

    private final ICollecteService  collecteService;
    private final ICreanceService   creanceService;
    private final JdbcTemplate      jdbcTemplate;

    // ── Collectes (BluCash) ───────────────────────────────────────────────────

    @Operation(
        summary = "Pousser une collecte terrain",
        description = "Enregistre un paiement reçu via Mobile Money ou espèces. Idempotent sur idCollecteMobile."
    )
    @PostMapping("/collectes")
    public ResponseEntity<CollecteResponse> pushCollecte(
            @Valid @RequestBody CollecteRequest request,
            @AuthenticationPrincipal User systemUser) {
        CollecteResponse resp = collecteService.enregistrer(request, systemUser);
        HttpStatus status = switch (resp.statut()) {
            case CONFIRMEE -> HttpStatus.CREATED;
            case DOUBLON   -> HttpStatus.CONFLICT;
            default        -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(resp);
    }

    // ── Créances (CBS) ────────────────────────────────────────────────────────

    @Operation(
        summary = "Mettre à jour le statut d'une créance",
        description = "Met à jour statut (ex: SOLDEE) et observation. Utilisé par le CBS après remboursement total."
    )
    @PutMapping("/creances/{uid}/statut")
    public ResponseEntity<CreanceResponse> updateCreanceStatut(
            @PathVariable UUID uid,
            @RequestParam String statut,
            @RequestParam(required = false, defaultValue = "") String observation) {
        return ResponseEntity.ok(creanceService.majStatut(uid, statut, observation));
    }

    @Operation(
        summary = "KPI recouvrement de l'IMF",
        description = "PAR30/60/90, taux de recouvrement et provisions COBAC. Utile pour le CBS."
    )
    @GetMapping("/creances/kpi")
    public ResponseEntity<KpiRecouvrementResponse> getKpi() {
        Long imfId = TenantContext.currentImfId();
        return ResponseEntity.ok(creanceService.kpiRecouvrement(imfId, null, null));
    }

    // ── Scores MCRS ───────────────────────────────────────────────────────────

    @Operation(
        summary = "Score MCRS d'un client",
        description = "Retourne le dernier score MCRS calculé par le pipeline ML. Calculé chaque jour à 6h00."
    )
    @GetMapping("/scores/{clientId}")
    public ResponseEntity<Map<String, Object>> getScore(@PathVariable String clientId) {
        Long imfId = TenantContext.currentImfId();
        String sql = """
                SELECT cs.client_id_externe,
                       i.code           AS imf_code,
                       cs.score_mcrs,
                       cs.niveau_risque,
                       cs.cobac_classe,
                       cs.cobac_provision_taux,
                       cs.probabilite_defaut_30j,
                       cs.probabilite_defaut_90j,
                       cs.action_recommandee,
                       cs.priorite_recouvrement,
                       cs.scored_at
                FROM ml.client_scores cs
                JOIN app.imf i ON i.id = cs.imf_id
                WHERE cs.imf_id = ? AND cs.client_id_externe = ?
                ORDER BY cs.scored_at DESC LIMIT 1
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, imfId, clientId);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(rows.get(0));
    }

    @Operation(
        summary = "Scores MCRS de tous les clients à risque élevé ou critique",
        description = "Retourne les clients dont le niveau de risque est ELEVE ou CRITIQUE. Limite 100."
    )
    @GetMapping("/scores/at-risk")
    public ResponseEntity<List<Map<String, Object>>> getAtRiskScores() {
        Long imfId = TenantContext.currentImfId();
        String sql = """
                SELECT cs.client_id_externe,
                       cs.score_mcrs,
                       cs.niveau_risque,
                       cs.cobac_classe,
                       cs.probabilite_defaut_30j,
                       cs.action_recommandee,
                       cs.priorite_recouvrement,
                       cs.scored_at
                FROM ml.client_scores cs
                WHERE cs.imf_id = ?
                  AND cs.niveau_risque IN ('ELEVE', 'CRITIQUE')
                ORDER BY cs.score_mcrs ASC, cs.priorite_recouvrement DESC
                LIMIT 100
                """;
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql, imfId));
    }

    // ── Alertes ───────────────────────────────────────────────────────────────

    @Operation(
        summary = "Alertes impayés actives",
        description = "Retourne les alertes d'impayés actives de l'IMF, triées par urgence."
    )
    @GetMapping("/alertes")
    public ResponseEntity<List<Map<String, Object>>> getAlertes(
            @RequestParam(defaultValue = "ACTIVE") String statut) {
        Long imfId = TenantContext.currentImfId();
        String sql = """
                SELECT a.uid::text AS id,
                       a.id_pret,
                       a.client_id,
                       a.jours_retard,
                       a.montant_en_retard,
                       a.statut_alerte,
                       a.created_at
                FROM app.alertes_impayes a
                WHERE a.imf_id = ?
                  AND a.statut_alerte = ?
                ORDER BY a.jours_retard DESC
                LIMIT 200
                """;
        return ResponseEntity.ok(jdbcTemplate.queryForList(sql, imfId, statut));
    }

    // ── Health check ──────────────────────────────────────────────────────────

    @Operation(summary = "Vérifier que la clé API est valide", description = "Retourne 200 si la clé est active.")
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(@AuthenticationPrincipal User systemUser) {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "imf", systemUser.getImf() != null ? systemUser.getImf().getNom() : "N/A",
                "timestamp", OffsetDateTime.now().toString()
        ));
    }
}
