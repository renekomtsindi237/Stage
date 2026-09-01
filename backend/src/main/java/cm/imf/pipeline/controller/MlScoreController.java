package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.request.TraiterAlerteRequest;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.MlAlerteResponse;
import cm.imf.pipeline.dto.response.MlScoreResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.ml.MlScoringClient;
import cm.imf.pipeline.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("/ml")
@RequiredArgsConstructor
@Tag(name = "ML Scoring", description = "Scores MCRS et alertes prédictives du pipeline ML")
public class MlScoreController {

    private static final List<String> STATUTS_TRAITEMENT =
            List.of("ACTIVE", "EN_TRAITEMENT", "RESOLUE", "IGNOREE");

    private static final String ALERTE_SELECT = """
            SELECT ap.id, ap.client_id_externe, ap.type_alerte, ap.urgence,
                   ap.titre, ap.description, ap.recommandation, ap.statut,
                   ap.resolution_note, ap.created_at,
                   COALESCE(ci.nom_complet, ap.client_id_externe) AS nom_client,
                   cr.encours, cr.jours_retard,
                   sc.score_mcrs, sc.probabilite_defaut_90j, sc.action_recommandee
            FROM ml.alertes_predictives ap
            LEFT JOIN app.clients_informels ci
                   ON ci.client_id_externe = ap.client_id_externe
                  AND ci.imf_id = ap.imf_id
            LEFT JOIN LATERAL (
                SELECT COALESCE(montant_impaye, 0) AS encours, jours_retard
                FROM app.creances
                WHERE imf_id = ap.imf_id
                  AND client_id_externe = ap.client_id_externe
                ORDER BY jours_retard DESC NULLS LAST
                LIMIT 1
            ) cr ON TRUE
            LEFT JOIN LATERAL (
                SELECT score_mcrs, probabilite_defaut_90j, action_recommandee
                FROM ml.client_scores
                WHERE imf_id = ap.imf_id
                  AND client_id_externe = ap.client_id_externe
                ORDER BY scored_at DESC
                LIMIT 1
            ) sc ON TRUE
            """;

    private static final String ALERTE_SELECT_SIMPLE = """
            SELECT ap.id, ap.client_id_externe, ap.type_alerte, ap.urgence,
                   ap.titre, ap.description, ap.recommandation, ap.statut,
                   ap.resolution_note, ap.created_at,
                   ap.client_id_externe AS nom_client,
                   NULL::numeric AS encours, NULL::int AS jours_retard,
                   NULL::numeric AS score_mcrs, NULL::numeric AS probabilite_defaut_90j,
                   NULL::text AS action_recommandee
            FROM ml.alertes_predictives ap
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MlScoringClient mlClient;

    // ─── Scores ──────────────────────────────────────────────────────────────

    @GetMapping("/score/{clientExterneId}")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','ANALYSTE','SUPER_ADMIN')")
    @Operation(summary = "Dernier score MCRS d'un client (batch quotidien)")
    public MlScoreResponse getScore(@PathVariable String clientExterneId) {
        Long imfId = requireImfId();
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
    public ResponseEntity<ApiResponse<List<MlAlerteResponse>>> getAlertes(
            @RequestParam(required = false) String statut) {
        Long imfId = requireImfId();
        String filter = (statut == null || statut.isBlank()) ? "" : " AND ap.statut = ? ";
        String order = """
                ORDER BY
                    CASE ap.urgence
                        WHEN 'CRITIQUE' THEN 1
                        WHEN 'HAUTE'    THEN 2
                        WHEN 'MOYENNE'  THEN 3
                        ELSE 4
                    END,
                    ap.created_at DESC
                LIMIT 200
                """;
        try {
            List<MlAlerteResponse> rows = queryAlertes(
                    ALERTE_SELECT + " WHERE ap.imf_id = ? " + filter + order, imfId, statut);
            return ResponseEntity.ok(ApiResponse.ok(rows));
        } catch (Exception e) {
            log.warn("GET /ml/alertes jointures indisponibles : {} — repli simple", e.getMessage());
            try {
                List<MlAlerteResponse> rows = queryAlertes(
                        ALERTE_SELECT_SIMPLE + " WHERE ap.imf_id = ? " + filter + order, imfId, statut);
                return ResponseEntity.ok(ApiResponse.ok(rows));
            } catch (Exception e2) {
                log.error("GET /ml/alertes impossible : {}", e2.getMessage(), e2);
                return ResponseEntity.ok(ApiResponse.ok(List.of()));
            }
        }
    }

    @GetMapping("/alertes/{id:\\d+}")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','ANALYSTE','SUPER_ADMIN')")
    @Operation(summary = "Détail d'une alerte prédictive")
    public ResponseEntity<ApiResponse<MlAlerteResponse>> getAlerte(@PathVariable long id) {
        Long imfId = requireImfId();
        MlAlerteResponse row = loadOne(id, imfId);
        if (row == null) {
            throw new ResponseStatusException(NOT_FOUND, "Alerte introuvable : " + id);
        }
        return ResponseEntity.ok(ApiResponse.ok(row));
    }

    @GetMapping("/alertes/client/{clientExterneId}")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','ANALYSTE','SUPER_ADMIN')")
    @Operation(summary = "Alertes prédictives d'un client")
    public ResponseEntity<ApiResponse<List<MlAlerteResponse>>> getAlertesClient(
            @PathVariable String clientExterneId) {
        Long imfId = requireImfId();
        try {
            List<MlAlerteResponse> rows = jdbcTemplate.query(
                    ALERTE_SELECT + """
                            WHERE ap.imf_id = ? AND ap.client_id_externe = ?
                            ORDER BY ap.created_at DESC LIMIT 50
                            """,
                    (rs, n) -> mapAlerte(rs), imfId, clientExterneId);
            return ResponseEntity.ok(ApiResponse.ok(rows));
        } catch (Exception e) {
            log.warn("GET /ml/alertes/client jointures indisponibles : {}", e.getMessage());
            List<MlAlerteResponse> rows = jdbcTemplate.query(
                    ALERTE_SELECT_SIMPLE + """
                            WHERE ap.imf_id = ? AND ap.client_id_externe = ?
                            ORDER BY ap.created_at DESC LIMIT 50
                            """,
                    (rs, n) -> mapAlerte(rs), imfId, clientExterneId);
            return ResponseEntity.ok(ApiResponse.ok(rows));
        }
    }

    @PutMapping("/alertes/{id}/statut")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','SUPER_ADMIN')")
    @Operation(summary = "Mise à jour du statut d'une alerte prédictive")
    public ResponseEntity<ApiResponse<MlAlerteResponse>> updateStatutAlerte(
            @PathVariable Long id,
            @RequestParam String statut,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(
                appliquerTraitement(id, statut, null, user)));
    }

    @PutMapping("/alertes/{id}/traitement")
    @PreAuthorize("hasAnyRole('DIRECTEUR','RESPONSABLE_RECOUVREMENT','DSI','SUPER_ADMIN')")
    @Operation(summary = "Traiter une alerte : statut, note, prise en charge")
    public ResponseEntity<ApiResponse<MlAlerteResponse>> traiterAlerte(
            @PathVariable Long id,
            @Valid @RequestBody TraiterAlerteRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(
                appliquerTraitement(id, req.statut(), req.note(), user)));
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

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Long requireImfId() {
        Long imfId = TenantContext.currentImfId();
        if (imfId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "IMF du compte introuvable");
        }
        return imfId;
    }

    private List<MlAlerteResponse> queryAlertes(String sql, Long imfId, String statut) {
        if (statut == null || statut.isBlank()) {
            return jdbcTemplate.query(sql, (rs, n) -> mapAlerte(rs), imfId);
        }
        return jdbcTemplate.query(sql, (rs, n) -> mapAlerte(rs), imfId, statut);
    }

    private MlAlerteResponse loadOne(long id, Long imfId) {
        try {
            List<MlAlerteResponse> rows = jdbcTemplate.query(
                    ALERTE_SELECT + " WHERE ap.id = ? AND ap.imf_id = ?",
                    (rs, n) -> mapAlerte(rs), id, imfId);
            if (!rows.isEmpty()) return rows.get(0);
        } catch (Exception e) {
            log.warn("Détail alerte jointures indisponibles : {}", e.getMessage());
        }
        List<MlAlerteResponse> rows = jdbcTemplate.query(
                ALERTE_SELECT_SIMPLE + " WHERE ap.id = ? AND ap.imf_id = ?",
                (rs, n) -> mapAlerte(rs), id, imfId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private MlAlerteResponse appliquerTraitement(Long id, String statut, String note, User user) {
        if (statut == null || !STATUTS_TRAITEMENT.contains(statut)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Statut invalide. Valeurs acceptées : ACTIVE, EN_TRAITEMENT, RESOLUE, IGNOREE");
        }
        Long imfId = requireImfId();
        Long userId = user != null ? user.getId() : null;
        String noteNorm = (note != null && !note.isBlank()) ? note.strip() : null;
        int updated = jdbcTemplate.update("""
                UPDATE ml.alertes_predictives
                SET statut = ?,
                    resolution_note = COALESCE(?, resolution_note),
                    prise_en_charge_par = COALESCE(prise_en_charge_par, ?),
                    prise_en_charge_at = COALESCE(prise_en_charge_at, NOW()),
                    updated_at = NOW()
                WHERE id = ? AND imf_id = ?
                """, statut, noteNorm, userId, id, imfId);
        if (updated == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Alerte introuvable : " + id);
        }
        MlAlerteResponse row = loadOne(id, imfId);
        if (row == null) {
            throw new ResponseStatusException(NOT_FOUND, "Alerte introuvable : " + id);
        }
        return row;
    }

    private MlScoreResponse mapScore(ResultSet rs) throws SQLException {
        Timestamp scored = rs.getTimestamp("scored_at");
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
                scored != null ? scored.toInstant().atOffset(java.time.ZoneOffset.UTC) : (OffsetDateTime) null
        );
    }

    private MlAlerteResponse mapAlerte(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new MlAlerteResponse(
                rs.getLong("id"),
                rs.getString("client_id_externe"),
                rs.getString("nom_client"),
                rs.getString("type_alerte"),
                rs.getString("urgence"),
                rs.getString("titre"),
                rs.getString("description"),
                rs.getString("recommandation"),
                rs.getString("statut"),
                created != null ? created.toInstant().toString() : null,
                rs.getString("resolution_note"),
                numberOrNull(rs, "encours"),
                intOrNull(rs, "jours_retard"),
                numberOrNull(rs, "score_mcrs"),
                numberOrNull(rs, "probabilite_defaut_90j"),
                rs.getString("action_recommandee")
        );
    }

    private static Double numberOrNull(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
