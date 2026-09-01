package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.MlAlerteResponse;
import cm.imf.pipeline.dto.response.MlScoreResponse;
import cm.imf.pipeline.ml.MlScoringClient;
import cm.imf.pipeline.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("/ml")
@RequiredArgsConstructor
@Tag(name = "ML Scoring", description = "Scores MCRS et alertes prédictives du pipeline ML")
public class MlScoreController {

    private final JdbcTemplate jdbcTemplate;
    private final MlScoringClient mlClient;

    // ─── Scores ──────────────────────────────────────────────────────────────

    @GetMapping("/score/{clientExterneId}")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','ANALYSTE','SUPER_ADMIN')")
    @Operation(summary = "Dernier score MCRS d'un client (batch quotidien)")
    public MlScoreResponse getScore(@PathVariable String clientExterneId) {
        Long imfId = TenantContext.currentImfId();
        String sql = """
                SELECT cs.client_id_externe,
                       i.code            AS imf_code,
                       cs.score_crs,
                       cs.score_rps,
                       cs.score_csi,
                       cs.score_mcrs,
                       cs.niveau_risque AS classe_risque,
                       cs.probabilite_defaut_30j,
                       cs.probabilite_defaut_90j,
                       cs.score_mcrs_ic_bas,
                       cs.score_mcrs_ic_haut,
                       cs.temps_survie_median_jours,
                       cs.action_recommandee,
                       cs.priorite_recouvrement,
                       cs.scored_at
                FROM ml.client_scores cs
                JOIN app.imf i ON i.id = cs.imf_id
                WHERE cs.imf_id = ?
                  AND cs.client_id_externe = ?
                ORDER BY cs.scored_at DESC
                LIMIT 1
                """;
        List<MlScoreResponse> rows = jdbcTemplate.query(
                sql, (rs, n) -> mapScore(rs), imfId, clientExterneId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND,
                    "Aucun score MCRS disponible pour le client " + clientExterneId
                    + ". Relancez le DAG dag_ml_scoring si le scoring n'a pas encore tourné aujourd'hui.");
        }
        return rows.get(0);
    }

    // ─── Alertes ─────────────────────────────────────────────────────────────

    @GetMapping("/alertes")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','ANALYSTE','SUPER_ADMIN')")
    @Operation(summary = "Alertes prédictives de l'IMF (200 dernières)")
    public List<MlAlerteResponse> getAlertes(
            @RequestParam(required = false) String statut) {
        Long imfId = TenantContext.currentImfId();
        String sql = """
                SELECT id, client_id_externe, type_alerte, urgence,
                       titre, description, recommandation, statut, created_at
                FROM ml.alertes_predictives
                WHERE imf_id = ?
                  AND (? IS NULL OR ? = '' OR statut = ?)
                ORDER BY
                    CASE urgence
                        WHEN 'CRITIQUE' THEN 1
                        WHEN 'HAUTE'    THEN 2
                        WHEN 'MOYENNE'  THEN 3
                        ELSE 4
                    END,
                    created_at DESC
                LIMIT 200
                """;
        return jdbcTemplate.query(sql, (rs, n) -> mapAlerte(rs), imfId, statut, statut, statut);
    }

    @GetMapping("/alertes/{clientExterneId}")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','ANALYSTE','SUPER_ADMIN')")
    @Operation(summary = "Alertes prédictives d'un client")
    public List<MlAlerteResponse> getAlertesClient(@PathVariable String clientExterneId) {
        Long imfId = TenantContext.currentImfId();
        String sql = """
                SELECT id, client_id_externe, type_alerte, urgence,
                       titre, description, recommandation, statut, created_at
                FROM ml.alertes_predictives
                WHERE imf_id = ?
                  AND client_id_externe = ?
                ORDER BY created_at DESC
                LIMIT 50
                """;
        return jdbcTemplate.query(sql, (rs, n) -> mapAlerte(rs), imfId, clientExterneId);
    }

    @PutMapping("/alertes/{id}/statut")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','SUPER_ADMIN')")
    @Operation(summary = "Mise à jour du statut d'une alerte prédictive")
    public ResponseEntity<Void> updateStatutAlerte(
            @PathVariable Long id,
            @RequestParam String statut) {
        if (!List.of("ACTIVE", "EN_TRAITEMENT", "RESOLUE", "IGNOREE").contains(statut)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Statut invalide. Valeurs acceptées : ACTIVE, EN_TRAITEMENT, RESOLUE, IGNOREE");
        }
        Long imfId = TenantContext.currentImfId();
        int updated = jdbcTemplate.update("""
                UPDATE ml.alertes_predictives
                SET statut = ?, updated_at = NOW()
                WHERE id = ? AND imf_id = ?
                """, statut, id, imfId);

        if (updated == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Alerte introuvable : " + id);
        }
        return ResponseEntity.noContent().build();
    }

    // ─── Modèle ML ───────────────────────────────────────────────────────────

    @GetMapping("/model/info")
    @PreAuthorize("hasAnyRole('DSI','ANALYSTE','SUPER_ADMIN')")
    @Operation(summary = "Métadonnées du modèle MCRS actif (version, AUC, features)")
    public ResponseEntity<Map<String, Object>> modelInfo() {
        return mlClient.modelInfo()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503).build());
    }

    @GetMapping("/model/health")
    @PreAuthorize("hasAnyRole('DSI','ANALYSTE','SUPER_ADMIN')")
    @Operation(summary = "Santé du service ML MCRS")
    public ResponseEntity<Map<String, Object>> modelHealth() {
        return mlClient.modelHealth()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(503).build());
    }

    // ─── Mappers JDBC ────────────────────────────────────────────────────────

    private MlScoreResponse mapScore(ResultSet rs) throws SQLException {
        return new MlScoreResponse(
                rs.getString("client_id_externe"),
                rs.getString("imf_code"),
                rs.getDouble("score_crs"),
                rs.getDouble("score_rps"),
                rs.getDouble("score_csi"),
                rs.getDouble("score_mcrs"),
                rs.getString("classe_risque"),
                rs.getDouble("probabilite_defaut_30j"),
                rs.getDouble("probabilite_defaut_90j"),
                rs.getDouble("score_mcrs_ic_bas"),
                rs.getDouble("score_mcrs_ic_haut"),
                rs.getObject("temps_survie_median_jours") != null
                        ? rs.getInt("temps_survie_median_jours") : null,
                rs.getString("action_recommandee"),
                rs.getInt("priorite_recouvrement"),
                rs.getObject("scored_at", OffsetDateTime.class)
        );
    }

    private MlAlerteResponse mapAlerte(ResultSet rs) throws SQLException {
        return new MlAlerteResponse(
                rs.getLong("id"),
                rs.getString("client_id_externe"),
                rs.getString("type_alerte"),
                rs.getString("urgence"),
                rs.getString("titre"),
                rs.getString("description"),
                rs.getString("recommandation"),
                rs.getString("statut"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
