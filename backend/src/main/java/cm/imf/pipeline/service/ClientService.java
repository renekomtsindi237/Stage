package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.ClientResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.security.DataMaskingUtils;
import cm.imf.pipeline.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientService implements IClientService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${imf.pipeline.staging-schema:staging}")
    private String stagingSchema;

    private static final RowMapper<ClientResponse> CLIENT_ROW_MAPPER = (rs, rowNum) ->
            new ClientResponse(
                    rs.getString("id_client"),
                    rs.getString("nom_client"),
                    rs.getString("telephone_client"),
                    rs.getString("agence_principale"),
                    rs.getDouble("encours"),
                    rs.getString("statut")
            );

    @Cacheable(value = "clients-search", key = "#query")
    public List<ClientResponse> search(String query, int limit) {
        String sql = """
                SELECT c.client_id_externe AS id_client,
                       c.nom_complet       AS nom_client,
                       c.telephone_principal AS telephone_client,
                       c.agence_code       AS agence_principale,
                       COALESCE(p.total_encours, 0) AS encours,
                       CASE
                           WHEN COALESCE(p.max_retard, 0) >= 90 THEN 'DEFAILLANT'
                           WHEN COALESCE(p.max_retard, 0) >= 30 THEN 'EN_RETARD'
                           ELSE 'ACTIF'
                       END AS statut
                FROM %s.stg_clients c
                LEFT JOIN (
                    SELECT nom_client,
                           SUM(solde_restant) AS total_encours,
                           MAX(jours_retard)  AS max_retard
                    FROM %s.stg_prets
                    WHERE statut_pret IN ('EN_COURS', 'EN_RETARD', 'EN_RECOUVREMENT')
                    GROUP BY nom_client
                ) p ON p.nom_client = c.nom_complet
                WHERE c.nom_complet ILIKE ?
                   OR c.telephone_principal LIKE ?
                ORDER BY c.nom_complet
                LIMIT ?
                """.formatted(stagingSchema, stagingSchema);
        String pattern = "%" + query + "%";
        return masquerSiNecessaire(jdbcTemplate.query(sql, CLIENT_ROW_MAPPER, pattern, pattern, limit));
    }

    public ClientResponse getById(String idClient) {
        String sql = """
                SELECT c.client_id_externe AS id_client,
                       c.nom_complet       AS nom_client,
                       c.telephone_principal AS telephone_client,
                       c.agence_code       AS agence_principale,
                       COALESCE(p.total_encours, 0) AS encours,
                       CASE
                           WHEN COALESCE(p.max_retard, 0) >= 90 THEN 'DEFAILLANT'
                           WHEN COALESCE(p.max_retard, 0) >= 30 THEN 'EN_RETARD'
                           ELSE 'ACTIF'
                       END AS statut
                FROM %s.stg_clients c
                LEFT JOIN (
                    SELECT nom_client,
                           SUM(solde_restant) AS total_encours,
                           MAX(jours_retard)  AS max_retard
                    FROM %s.stg_prets
                    WHERE statut_pret IN ('EN_COURS', 'EN_RETARD', 'EN_RECOUVREMENT')
                    GROUP BY nom_client
                ) p ON p.nom_client = c.nom_complet
                WHERE c.client_id_externe = ?
                """.formatted(stagingSchema, stagingSchema);

        ClientResponse client = jdbcTemplate.query(sql, CLIENT_ROW_MAPPER, idClient)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Client", idClient));
        return masquerSiNecessaire(client);
    }

    public List<ClientResponse> list(int page, int size, String search, String statut, String agence) {
        List<Object> params = new ArrayList<>();

        StringBuilder inner = new StringBuilder("""
                SELECT c.client_id_externe AS id_client,
                       c.nom_complet       AS nom_client,
                       c.telephone_principal AS telephone_client,
                       c.agence_code       AS agence_principale,
                       COALESCE(p.total_encours, 0) AS encours,
                       CASE
                           WHEN COALESCE(p.max_retard, 0) >= 90 THEN 'DEFAILLANT'
                           WHEN COALESCE(p.max_retard, 0) >= 30 THEN 'EN_RETARD'
                           ELSE 'ACTIF'
                       END AS statut
                FROM %s.stg_clients c
                LEFT JOIN (
                    SELECT nom_client,
                           SUM(solde_restant) AS total_encours,
                           MAX(jours_retard)  AS max_retard
                    FROM %s.stg_prets
                    WHERE statut_pret IN ('EN_COURS', 'EN_RETARD', 'EN_RECOUVREMENT')
                    GROUP BY nom_client
                ) p ON p.nom_client = c.nom_complet
                WHERE 1=1
                """.formatted(stagingSchema, stagingSchema));

        if (search != null && !search.isBlank()) {
            inner.append(" AND (c.nom_complet ILIKE ? OR c.telephone_principal LIKE ?)");
            params.add("%" + search.trim() + "%");
            params.add("%" + search.trim() + "%");
        }
        if (agence != null && !agence.isBlank()) {
            inner.append(" AND c.agence_code ILIKE ?");
            params.add("%" + agence.trim() + "%");
        }

        String sql;
        if (statut != null && !statut.isBlank()) {
            sql = "SELECT * FROM (" + inner + ") sub WHERE sub.statut = ? ORDER BY nom_client LIMIT ? OFFSET ?";
            params.add(statut.trim().toUpperCase());
        } else {
            sql = inner + " ORDER BY c.nom_complet LIMIT ? OFFSET ?";
        }
        params.add(size);
        params.add((long) page * size);

        return masquerSiNecessaire(jdbcTemplate.query(sql, CLIENT_ROW_MAPPER, params.toArray()));
    }

    public long count(String search, String statut, String agence) {
        List<Object> params = new ArrayList<>();

        StringBuilder inner = new StringBuilder("""
                SELECT CASE
                           WHEN COALESCE(p.max_retard, 0) >= 90 THEN 'DEFAILLANT'
                           WHEN COALESCE(p.max_retard, 0) >= 30 THEN 'EN_RETARD'
                           ELSE 'ACTIF'
                       END AS statut
                FROM %s.stg_clients c
                LEFT JOIN (
                    SELECT nom_client, MAX(jours_retard) AS max_retard
                    FROM %s.stg_prets
                    WHERE statut_pret IN ('EN_COURS', 'EN_RETARD', 'EN_RECOUVREMENT')
                    GROUP BY nom_client
                ) p ON p.nom_client = c.nom_complet
                WHERE 1=1
                """.formatted(stagingSchema, stagingSchema));

        if (search != null && !search.isBlank()) {
            inner.append(" AND (c.nom_complet ILIKE ? OR c.telephone_principal LIKE ?)");
            params.add("%" + search.trim() + "%");
            params.add("%" + search.trim() + "%");
        }
        if (agence != null && !agence.isBlank()) {
            inner.append(" AND c.agence_code ILIKE ?");
            params.add("%" + agence.trim() + "%");
        }

        String sql;
        if (statut != null && !statut.isBlank()) {
            sql = "SELECT COUNT(*) FROM (" + inner + ") sub WHERE sub.statut = ?";
            params.add(statut.trim().toUpperCase());
        } else {
            sql = "SELECT COUNT(*) FROM (" + inner + ") sub";
        }

        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    // ── Masquage PII selon rôle ──────────────────────────────────────────────

    private ClientResponse masquerSiNecessaire(ClientResponse client) {
        if (client == null) return null;
        Role role = roleAppelant();
        if (DataMaskingUtils.peutVoirDonneesCompletes(role)) return client;
        return new ClientResponse(
                client.idClient(),
                DataMaskingUtils.masquerNom(client.nomClient()),
                DataMaskingUtils.masquerTelephone(client.telephoneClient()),
                client.agencePrincipale(),
                client.encours(),
                client.statut()
        );
    }

    private List<ClientResponse> masquerSiNecessaire(List<ClientResponse> clients) {
        Role role = roleAppelant();
        if (DataMaskingUtils.peutVoirDonneesCompletes(role)) return clients;
        return clients.stream().map(this::masquerSiNecessaire).toList();
    }

    private Role roleAppelant() {
        User user = TenantContext.currentUser();
        return user != null ? user.getRole() : Role.AGENT;
    }
}
