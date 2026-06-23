package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.dto.request.PositionRequest;
import cm.imf.pipeline.dto.response.AgentPositionResponse;
import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.entity.Consentement;
import cm.imf.pipeline.exception.BusinessException;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.ConsentementRepository;
import cm.imf.pipeline.service.IPositionService;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implémentation du service de géolocalisation des agents terrain.
 *
 * Choix technique : JdbcTemplate (comme AgentServiceImpl) pour rester cohérent
 * avec le reste du backend et éviter les requêtes N+1 de JPA sur les positions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements IPositionService {

    private final JdbcTemplate         jdbc;
    private final SseEmitterRegistry   sseRegistry;
    private final ConsentementRepository consentementRepository;

    // RowMapper partagé (liste + historique)
    private static final RowMapper<AgentPositionResponse> POSITION_ROW_MAPPER =
            new PositionRowMapper();

    // ── Mise à jour de position ───────────────────────────────────────────────

    @Override
    @Transactional
    public AgentPositionResponse mettreAJourPosition(Long agentId, Long imfId,
                                                      PositionRequest req) {

        // 0) Vérifier que le DSI a accordé le consentement de géolocalisation pour cet agent.
        //    Art. 9 et 50 — Loi 2024/017 : consentement préalable obligatoire.
        //    C'est le DSI de l'IMF (représentant l'employeur) qui accorde ce consentement,
        //    pas l'agent lui-même, conformément au cadre professionnel de la microfinance.
        boolean consentementAccorde = consentementRepository
                .existsByImfIdAndSujetTypeAndSujetIdAndFinaliteAndAccordeTrue(
                        imfId, "AGENT", agentId, Consentement.FINALITE_GEOLOCALISATION);

        if (!consentementAccorde) {
            throw new BusinessException(
                    "Géolocalisation non autorisée pour cet agent : consentement DSI absent. " +
                    "Le DSI doit accorder le consentement via PUT /api/admin/rgpd/consentements/agents/{agentId}/GEOLOCALISATION");
        }

        // 1) Upsert dernière position dans app.utilisateurs
        jdbc.update("""
                UPDATE app.utilisateurs
                SET latitude              = ?,
                    longitude             = ?,
                    precision_gps_m       = ?,
                    derniere_position_at  = NOW(),
                    position_active       = TRUE,
                    updated_at            = NOW()
                WHERE id = ?
                  AND imf_id = ?
                """,
                req.latitude(), req.longitude(), req.precisionMetres(),
                agentId, imfId);

        // 2) Insertion dans l'historique
        UUID collecteUuid = null;
        if (req.collecteUuid() != null && !req.collecteUuid().isBlank()) {
            try { collecteUuid = UUID.fromString(req.collecteUuid()); }
            catch (IllegalArgumentException ignored) {
                log.warn("collecteUuid invalide ignoré : {}", req.collecteUuid());
            }
        }

        jdbc.update("""
                INSERT INTO app.positions_agents
                    (imf_id, agent_id, latitude, longitude,
                     precision_gps_m, altitude_m, vitesse_kmh, cap_degres,
                     source, collecte_uuid, captured_at)
                VALUES (?, ?, ?, ?,  ?, ?, ?, ?,  ?, ?, NOW())
                """,
                imfId, agentId,
                req.latitude(), req.longitude(),
                req.precisionMetres(), req.altitudeMetres(),
                req.vitesseKmh(), req.capDegres(),
                req.source(), collecteUuid);

        // 3) Lire la position enrichie (avec nom, agence)
        AgentPositionResponse position = chargerPositionAgent(agentId, imfId);

        // 4) Push SSE aux responsables de l'IMF
        SseEventDto event = SseEventDto.agentPositionUpdated(position);
        sseRegistry.broadcastToRole("RESPONSABLE_RECOUVREMENT", event);
        sseRegistry.broadcastToRole("DIRECTEUR", event);

        log.debug("Position agent {} mise à jour : ({}, {})", agentId,
                req.latitude(), req.longitude());

        return position;
    }

    // ── Désactivation du partage ──────────────────────────────────────────────

    @Override
    @Transactional
    public void desactiverPartage(Long agentId, Long imfId) {
        int updated = jdbc.update("""
                UPDATE app.utilisateurs
                SET position_active = FALSE,
                    updated_at      = NOW()
                WHERE id = ?
                  AND imf_id = ?
                """, agentId, imfId);

        if (updated == 0) {
            throw new ResourceNotFoundException("Agent", agentId.toString());
        }
        log.info("Partage de position désactivé pour agent {}", agentId);
    }

    // ── Liste des agents actifs (carte) ───────────────────────────────────────

    @Override
    public List<AgentPositionResponse> listerPositionsActives(Long imfId, Long agenceId) {
        if (agenceId != null) {
            return jdbc.query("""
                    SELECT
                        u.uid::text      AS agent_uid,
                        u.id             AS agent_id,
                        u.username,
                        u.username       AS nom_complet,
                        NULL::TEXT       AS nom_agence,
                        NULL::TEXT       AS ville_agence,
                        u.latitude,
                        u.longitude,
                        u.precision_gps_m,
                        NULL::NUMERIC    AS altitude_m,
                        NULL::NUMERIC    AS vitesse_kmh,
                        (u.derniere_position_at > NOW() - INTERVAL '15 minutes'
                         AND u.position_active = TRUE)  AS en_deplacement,
                        'MOBILE'         AS source,
                        u.derniere_position_at           AS captured_at
                    FROM app.utilisateurs u
                    JOIN app.cycles_collecte cc ON cc.imf_id = u.imf_id AND cc.agence_id = ?
                    WHERE u.imf_id   = ?
                      AND u.role      = 'AGENT'
                      AND u.actif     = TRUE
                      AND u.latitude  IS NOT NULL
                      AND u.position_active = TRUE
                    ORDER BY en_deplacement DESC, u.username
                    """,
                    POSITION_ROW_MAPPER, agenceId, imfId);
        }

        return jdbc.query("""
                SELECT
                    u.uid::text      AS agent_uid,
                    u.id             AS agent_id,
                    u.username,
                    u.username       AS nom_complet,
                    NULL::TEXT       AS nom_agence,
                    NULL::TEXT       AS ville_agence,
                    u.latitude,
                    u.longitude,
                    u.precision_gps_m,
                    NULL::NUMERIC    AS altitude_m,
                    NULL::NUMERIC    AS vitesse_kmh,
                    (u.derniere_position_at > NOW() - INTERVAL '15 minutes'
                     AND u.position_active = TRUE)  AS en_deplacement,
                    'MOBILE'         AS source,
                    u.derniere_position_at           AS captured_at
                FROM app.utilisateurs u
                WHERE u.imf_id  = ?
                  AND u.role    = 'AGENT'
                  AND u.actif   = TRUE
                  AND u.latitude IS NOT NULL
                  AND u.position_active = TRUE
                ORDER BY en_deplacement DESC, u.username
                """,
                POSITION_ROW_MAPPER, imfId);
    }

    // ── Historique journalier (trajet) ────────────────────────────────────────

    @Override
    public List<AgentPositionResponse> historiqueJournalier(Long agentId, Long imfId,
                                                             LocalDate date) {
        LocalDate jour = (date != null) ? date : LocalDate.now();
        return jdbc.query("""
                SELECT
                    u.uid::text      AS agent_uid,
                    u.id             AS agent_id,
                    u.username,
                    u.username       AS nom_complet,
                    NULL::TEXT       AS nom_agence,
                    NULL::TEXT       AS ville_agence,
                    p.latitude,
                    p.longitude,
                    p.precision_gps_m,
                    p.altitude_m,
                    p.vitesse_kmh,
                    FALSE            AS en_deplacement,
                    p.source,
                    p.captured_at
                FROM app.positions_agents p
                JOIN app.utilisateurs u ON u.id = p.agent_id
                WHERE p.agent_id  = ?
                  AND p.imf_id    = ?
                  AND p.captured_at::DATE = ?
                ORDER BY p.captured_at
                LIMIT 500
                """,
                POSITION_ROW_MAPPER, agentId, imfId, jour);
    }

    // ── Helpers privés ────────────────────────────────────────────────────────

    private AgentPositionResponse chargerPositionAgent(Long agentId, Long imfId) {
        return jdbc.query("""
                SELECT
                    u.uid::text      AS agent_uid,
                    u.id             AS agent_id,
                    u.username,
                    u.username       AS nom_complet,
                    NULL::TEXT       AS nom_agence,
                    NULL::TEXT       AS ville_agence,
                    u.latitude, u.longitude, u.precision_gps_m,
                    NULL::NUMERIC AS altitude_m, NULL::NUMERIC AS vitesse_kmh,
                    (u.derniere_position_at > NOW() - INTERVAL '15 minutes'
                     AND u.position_active = TRUE) AS en_deplacement,
                    'MOBILE' AS source,
                    u.derniere_position_at AS captured_at
                FROM app.utilisateurs u
                WHERE u.id = ? AND u.imf_id = ?
                """,
                POSITION_ROW_MAPPER, agentId, imfId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Agent", agentId.toString()));
    }

    // ── RowMapper ─────────────────────────────────────────────────────────────

    private static class PositionRowMapper implements RowMapper<AgentPositionResponse> {
        @Override
        public AgentPositionResponse mapRow(ResultSet rs, int rowNum) throws SQLException {
            OffsetDateTime capturedAt = null;
            var ts = rs.getTimestamp("captured_at");
            if (ts != null) {
                capturedAt = ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
            }
            return new AgentPositionResponse(
                    rs.getString("agent_uid"),
                    rs.getString("username"),
                    rs.getString("nom_complet"),
                    rs.getString("nom_agence"),
                    rs.getString("ville_agence"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getObject("precision_gps_m", Double.class),
                    rs.getObject("altitude_m",      Double.class),
                    rs.getObject("vitesse_kmh",     Double.class),
                    rs.getBoolean("en_deplacement"),
                    rs.getString("source"),
                    capturedAt
            );
        }
    }
}
