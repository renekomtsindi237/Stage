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
                    rs.getString("agence_principale")
            );

    @Cacheable(value = "clients-search", key = "#query")
    public List<ClientResponse> search(String query, int limit) {
        String sql = """
                SELECT client_id_externe AS id_client,
                       nom_complet       AS nom_client,
                       telephone_principal AS telephone_client,
                       agence_code       AS agence_principale
                FROM %s.stg_clients
                WHERE nom_complet ILIKE ?
                   OR telephone_principal LIKE ?
                ORDER BY nom_complet
                LIMIT ?
                """.formatted(stagingSchema);
        String pattern = "%" + query + "%";
        List<ClientResponse> resultats = jdbcTemplate.query(sql, CLIENT_ROW_MAPPER,
                pattern, pattern, limit);
        return masquerSiNecessaire(resultats);
    }

    public ClientResponse getById(String idClient) {
        String sql = """
                SELECT client_id_externe AS id_client,
                       nom_complet       AS nom_client,
                       telephone_principal AS telephone_client,
                       agence_code       AS agence_principale
                FROM %s.stg_clients
                WHERE client_id_externe = ?
                """.formatted(stagingSchema);

        ClientResponse client = jdbcTemplate.query(sql, CLIENT_ROW_MAPPER, idClient)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Client", idClient));
        return masquerSiNecessaire(client);
    }

    public List<ClientResponse> list(int page, int size) {
        String sql = """
                SELECT client_id_externe AS id_client,
                       nom_complet       AS nom_client,
                       telephone_principal AS telephone_client,
                       agence_code       AS agence_principale
                FROM %s.stg_clients
                ORDER BY nom_complet
                LIMIT ? OFFSET ?
                """.formatted(stagingSchema);
        List<ClientResponse> resultats = jdbcTemplate.query(sql, CLIENT_ROW_MAPPER,
                size, (long) page * size);
        return masquerSiNecessaire(resultats);
    }

    public long count() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s.stg_clients".formatted(stagingSchema), Long.class);
    }

    // ── Masquage PII selon rôle de l'appelant ────────────────────────────────

    /**
     * Les ANALYSTE voient les données masquées — ils ont besoin des tendances
     * agrégées, pas des identités individuelles.
     * Les AGENT ne voient que leurs propres clients via les collectes — ce service
     * ne devrait pas être appelé directement par un AGENT.
     * DSI, RR, DIRECTEUR, SUPER_ADMIN : données complètes.
     */
    private ClientResponse masquerSiNecessaire(ClientResponse client) {
        if (client == null) return null;
        Role role = roleAppelant();
        if (DataMaskingUtils.peutVoirDonneesCompletes(role)) return client;
        return new ClientResponse(
                client.idClient(),
                DataMaskingUtils.masquerNom(client.nomClient()),
                DataMaskingUtils.masquerTelephone(client.telephoneClient()),
                client.agencePrincipale()   // agence : donnée non-PII
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
