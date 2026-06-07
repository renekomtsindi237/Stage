package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.dto.response.AgentResponse;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.service.IAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service de consultation des agents terrain.
 *
 * Source de données : app.utilisateurs (agents = users avec role='AGENT').
 * Le schéma staging (stg_agents) était utilisé dans l'ancienne architecture
 * avec MTN/Orange — on lit désormais directement app.utilisateurs pour
 * avoir accès aux données GPS et au statut en ligne.
 *
 * Le statut en ligne (enLigne) est déterminé via Redis :
 * clé "online:{userId}:{imfId}" avec TTL 5 min (posée par JwtAuthenticationFilter).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements IAgentService {

    private final JdbcTemplate         jdbc;
    private final StringRedisTemplate  redis;

    @Value("${imf.pipeline.staging-schema:staging}")
    private String stagingSchema;   // conservé pour compatibilité (non utilisé)

    private static final String BASE_SQL = """
            SELECT
                u.uid::text      AS agent_uid,
                u.id             AS agent_id,
                u.username,
                u.username       AS nom_complet,
                NULL::TEXT       AS id_agence,
                NULL::TEXT       AS nom_agence,
                NULL::TEXT       AS ville_agence,
                u.email          AS telephone,
                u.latitude,
                u.longitude,
                u.precision_gps_m,
                u.position_active,
                (u.derniere_position_at > NOW() - INTERVAL '15 minutes'
                 AND u.position_active = TRUE) AS en_deplacement,
                u.derniere_position_at,
                u.imf_id
            FROM app.utilisateurs u
            WHERE u.role  = 'AGENT'
              AND u.actif = TRUE
            """;

    @Override
    @Cacheable(value = "agents-agence", key = "#imfId + '_' + #idAgence")
    public List<AgentResponse> listByAgence(String idAgence) {
        return jdbc.query(
                BASE_SQL + " ORDER BY u.username LIMIT 100",
                agentRowMapper());
    }

    @Override
    @Cacheable(value = "agents-list", key = "#page + '_' + #size")
    public List<AgentResponse> listAll(int page, int size) {
        return jdbc.query(
                BASE_SQL + " ORDER BY u.username LIMIT ? OFFSET ?",
                agentRowMapper(), size, (long) page * size);
    }

    @Override
    public long count() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM app.utilisateurs WHERE role='AGENT' AND actif=TRUE",
                Long.class);
    }

    @Override
    public AgentResponse getById(String idAgent) {
        return jdbc.query(
                BASE_SQL + " AND u.id = ?",
                agentRowMapper(), Long.parseLong(idAgent))
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Agent", idAgent));
    }

    @Override
    @Cacheable(value = "agents-search", key = "#query + '_' + #limit")
    public List<AgentResponse> search(String query, int limit) {
        return jdbc.query(
                BASE_SQL + " AND u.username ILIKE ? ORDER BY u.username LIMIT ?",
                agentRowMapper(), "%" + query + "%", limit);
    }

    // ── RowMapper ─────────────────────────────────────────────────────────────

    private RowMapper<AgentResponse> agentRowMapper() {
        return (rs, rowNum) -> {
            Long agentId = rs.getLong("agent_id");
            Long imfId   = rs.getLong("imf_id");

            // Statut en ligne via Redis (clé posée par JwtAuthenticationFilter)
            boolean enLigne = Boolean.TRUE.equals(
                    redis.hasKey("online:" + agentId + ":" + imfId));

            OffsetDateTime dernierePosition = null;
            var ts = rs.getTimestamp("derniere_position_at");
            if (ts != null) {
                dernierePosition = ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
            }

            // GPS null si l'agent n'a pas partagé sa position
            double lat = rs.getDouble("latitude");
            Double latitude  = rs.wasNull() ? null : lat;
            double lng = rs.getDouble("longitude");
            Double longitude = rs.wasNull() ? null : lng;

            double precRaw = rs.getDouble("precision_gps_m");
            Double precision = rs.wasNull() ? null : precRaw;

            return new AgentResponse(
                    rs.getString("agent_uid"),
                    rs.getString("username"),
                    rs.getString("nom_complet"),
                    rs.getString("id_agence"),
                    rs.getString("nom_agence"),
                    rs.getString("ville_agence"),
                    rs.getString("telephone"),
                    latitude,
                    longitude,
                    precision,
                    enLigne,
                    rs.getBoolean("en_deplacement"),
                    dernierePosition
            );
        };
    }
}
