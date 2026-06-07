package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.PretResponse;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PretService implements IPretService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${imf.pipeline.staging-schema:staging}")
    private String stagingSchema;

    private static final RowMapper<PretResponse> PRET_ROW_MAPPER = (rs, rowNum) ->
            new PretResponse(
                    rs.getString("id_pret"),
                    rs.getString("id_client"),
                    rs.getString("nom_client"),
                    rs.getString("nom_agence"),
                    rs.getString("nom_produit"),
                    rs.getString("nom_agent"),
                    rs.getBigDecimal("montant_pret"),
                    toLocalDate(rs.getDate("date_deblocage")),
                    toLocalDate(rs.getDate("date_echeance")),
                    nullSafeBd(rs, "montant_rembourse"),
                    nullSafeBd(rs, "solde_restant"),
                    rs.getString("statut_pret"),
                    rs.getInt("jours_retard")
            );

    /**
     * Liste paginée des prêts (tous statuts), triée par jours de retard décroissant.
     */
    @Cacheable(value = "prets-list", key = "#page + '_' + #size + '_' + #statut")
    public List<PretResponse> listPrets(String statut, int page, int size) {
        String whereClause = statut != null ? "WHERE statut_pret = ?" : "";
        Object[] args = statut != null
                ? new Object[]{statut, size, page * size}
                : new Object[]{size, page * size};

        String sql = """
                SELECT id_pret, id_client, nom_client, nom_agence, nom_produit, nom_agent,
                       montant_pret, date_deblocage, date_echeance, montant_rembourse,
                       solde_restant, statut_pret, jours_retard
                FROM %s.stg_prets
                %s
                ORDER BY jours_retard DESC, date_echeance ASC
                LIMIT ? OFFSET ?
                """.formatted(stagingSchema, whereClause);

        return jdbcTemplate.query(sql, PRET_ROW_MAPPER, args);
    }

    /**
     * Total pour la pagination.
     */
    public long countPrets(String statut) {
        if (statut != null) {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM %s.stg_prets WHERE statut_pret = ?".formatted(stagingSchema),
                    Long.class, statut);
        }
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s.stg_prets".formatted(stagingSchema), Long.class);
    }

    /**
     * Détail d'un prêt par son ID.
     */
    public PretResponse getById(String idPret) {
        String sql = """
                SELECT id_pret, id_client, nom_client, nom_agence, nom_produit, nom_agent,
                       montant_pret, date_deblocage, date_echeance, montant_rembourse,
                       solde_restant, statut_pret, jours_retard
                FROM %s.stg_prets
                WHERE id_pret = ?
                """.formatted(stagingSchema);

        return jdbcTemplate.query(sql, PRET_ROW_MAPPER, idPret)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Prêt", idPret));
    }

    /**
     * Prêts actifs d'un client (pour la fiche client).
     */
    public List<PretResponse> getPretsClient(String idClient) {
        String sql = """
                SELECT id_pret, id_client, nom_client, nom_agence, nom_produit, nom_agent,
                       montant_pret, date_deblocage, date_echeance, montant_rembourse,
                       solde_restant, statut_pret, jours_retard
                FROM %s.stg_prets
                WHERE id_client = ?
                ORDER BY date_deblocage DESC
                """.formatted(stagingSchema);

        return jdbcTemplate.query(sql, PRET_ROW_MAPPER, idClient);
    }

    /**
     * Prêts gérés par un agent (pour l'app mobile — permet à l'agent de sélectionner un prêt).
     */
    @Cacheable(value = "prets-agent", key = "#nomAgent")
    public List<PretResponse> getPretsAgent(String nomAgent) {
        String sql = """
                SELECT id_pret, id_client, nom_client, nom_agence, nom_produit, nom_agent,
                       montant_pret, date_deblocage, date_echeance, montant_rembourse,
                       solde_restant, statut_pret, jours_retard
                FROM %s.stg_prets
                WHERE nom_agent ILIKE ?
                  AND statut_pret NOT IN ('SOLDE', 'PERTE')
                ORDER BY jours_retard DESC, date_echeance ASC
                """.formatted(stagingSchema);

        return jdbcTemplate.query(sql, PRET_ROW_MAPPER, "%" + nomAgent + "%");
    }

    private static java.time.LocalDate toLocalDate(Date d) {
        return d != null ? d.toLocalDate() : null;
    }

    private static BigDecimal nullSafeBd(java.sql.ResultSet rs, String col) throws java.sql.SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return rs.wasNull() ? BigDecimal.ZERO : v;
    }
}
