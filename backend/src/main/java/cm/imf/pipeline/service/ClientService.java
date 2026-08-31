package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.response.ClientDossierResponse;
import cm.imf.pipeline.dto.response.ClientDossierResponse.CollecteResume;
import cm.imf.pipeline.dto.response.ClientDossierResponse.CreanceResume;
import cm.imf.pipeline.dto.response.ClientDossierResponse.KycResume;
import cm.imf.pipeline.dto.response.ClientResponse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.security.DataMaskingUtils;
import cm.imf.pipeline.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService implements IClientService {

    private final JdbcTemplate jdbcTemplate;

    private static final String CREANCES_ACTIVES =
            "'ACTIVE','RECOUVREMENT_AMIABLE','MISE_EN_DEMEURE','CONTENTIEUX','REECHELONNEE'";

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
                       a.nom               AS agence_principale,
                       COALESCE(cr.total_encours, 0) AS encours,
                       CASE
                           WHEN COALESCE(cr.max_retard, 0) >= 90 THEN 'DEFAILLANT'
                           WHEN COALESCE(cr.max_retard, 0) >= 30 THEN 'EN_RETARD'
                           ELSE 'ACTIF'
                       END AS statut
                FROM app.clients_informels c
                LEFT JOIN app.agences a ON a.id = c.agence_id
                LEFT JOIN (
                    SELECT client_informel_id,
                           SUM(montant_impaye) AS total_encours,
                           MAX(jours_retard)   AS max_retard
                    FROM app.creances
                    WHERE statut IN (%s)
                    GROUP BY client_informel_id
                ) cr ON cr.client_informel_id = c.id
                WHERE c.imf_id = ?
                  AND (c.nom_complet ILIKE ? OR c.telephone_principal LIKE ?)
                ORDER BY c.nom_complet
                LIMIT ?
                """.formatted(CREANCES_ACTIVES);
        String pattern = "%" + query + "%";
        Long imfId = TenantContext.currentImfId();
        return masquerSiNecessaire(jdbcTemplate.query(sql, CLIENT_ROW_MAPPER, imfId, pattern, pattern, limit));
    }

    public ClientResponse getById(String idClient) {
        String sql = """
                SELECT c.client_id_externe AS id_client,
                       c.nom_complet       AS nom_client,
                       c.telephone_principal AS telephone_client,
                       a.nom               AS agence_principale,
                       COALESCE(cr.total_encours, 0) AS encours,
                       CASE
                           WHEN COALESCE(cr.max_retard, 0) >= 90 THEN 'DEFAILLANT'
                           WHEN COALESCE(cr.max_retard, 0) >= 30 THEN 'EN_RETARD'
                           ELSE 'ACTIF'
                       END AS statut
                FROM app.clients_informels c
                LEFT JOIN app.agences a ON a.id = c.agence_id
                LEFT JOIN (
                    SELECT client_informel_id,
                           SUM(montant_impaye) AS total_encours,
                           MAX(jours_retard)   AS max_retard
                    FROM app.creances
                    WHERE statut IN (%s)
                    GROUP BY client_informel_id
                ) cr ON cr.client_informel_id = c.id
                WHERE c.imf_id = ? AND c.client_id_externe = ?
                """.formatted(CREANCES_ACTIVES);

        Long imfId = TenantContext.currentImfId();
        ClientResponse client = jdbcTemplate.query(sql, CLIENT_ROW_MAPPER, imfId, idClient)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Client", idClient));
        return masquerSiNecessaire(client);
    }

    public ClientDossierResponse getDossier(String idClient) {
        String sql = """
                SELECT c.client_id_externe AS id_client,
                       c.nom_complet       AS nom_client,
                       c.telephone_principal AS telephone_client,
                       c.telephone_secondaire,
                       a.nom               AS agence_principale,
                       c.zone_id,
                       c.actif,
                       c.date_naissance,
                       c.sexe,
                       c.secteur_principal,
                       c.sous_secteur,
                       c.annees_experience,
                       c.revenu_mensuel_estime,
                       c.marche_principal,
                       c.frequence_marche,
                       c.niveau_education,
                       c.situation_familiale,
                       c.nombre_personnes_charge,
                       c.latitude_activite,
                       c.longitude_activite,
                       c.adresse_activite,
                       c.created_at,
                       COALESCE(cr.total_encours, 0) AS encours,
                       COALESCE(cr.max_retard, 0)    AS max_jours_retard,
                       CASE
                           WHEN COALESCE(cr.max_retard, 0) >= 90 THEN 'DEFAILLANT'
                           WHEN COALESCE(cr.max_retard, 0) >= 30 THEN 'EN_RETARD'
                           ELSE 'ACTIF'
                       END AS statut
                FROM app.clients_informels c
                LEFT JOIN app.agences a ON a.id = c.agence_id
                LEFT JOIN (
                    SELECT client_informel_id,
                           SUM(montant_impaye) AS total_encours,
                           MAX(jours_retard)   AS max_retard
                    FROM app.creances
                    WHERE statut IN (%s)
                    GROUP BY client_informel_id
                ) cr ON cr.client_informel_id = c.id
                WHERE c.imf_id = ? AND c.client_id_externe = ?
                """.formatted(CREANCES_ACTIVES);

        Long imfId = TenantContext.currentImfId();
        ClientDossierResponse dossier = jdbcTemplate.query(sql, (rs, n) -> mapDossierIdentite(rs), imfId, idClient)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Client", idClient));

        List<CreanceResume> creances = jdbcTemplate.query("""
                SELECT id_pret_externe, statut, montant_initial, montant_impaye, capital_restant_du,
                       jours_retard, categorie_par, type_garantie, date_deblocage, date_ouverture_creance
                FROM app.creances
                WHERE imf_id = ? AND client_id_externe = ?
                ORDER BY jours_retard DESC, created_at DESC
                LIMIT 50
                """, (rs, n) -> new CreanceResume(
                        rs.getString("id_pret_externe"),
                        rs.getString("statut"),
                        decimal(rs, "montant_initial"),
                        decimal(rs, "montant_impaye"),
                        decimal(rs, "capital_restant_du"),
                        intOrZero(rs, "jours_retard"),
                        rs.getString("categorie_par"),
                        rs.getString("type_garantie"),
                        dateStr(rs, "date_deblocage"),
                        dateStr(rs, "date_ouverture_creance")
                ), imfId, idClient);

        List<CollecteResume> collectes = jdbcTemplate.query("""
                SELECT ct.id_collecte_mobile, ct.montant_collecte, ct.canal_paiement,
                       ct.date_collecte, ct.statut, u.username AS agent_username
                FROM app.collectes_terrain ct
                LEFT JOIN app.utilisateurs u ON u.id = ct.agent_id
                WHERE ct.imf_id = ? AND ct.client_id = ?
                ORDER BY ct.date_collecte DESC, ct.created_at DESC
                LIMIT 30
                """, (rs, n) -> new CollecteResume(
                        rs.getString("id_collecte_mobile"),
                        decimal(rs, "montant_collecte"),
                        rs.getString("canal_paiement"),
                        dateStr(rs, "date_collecte"),
                        rs.getString("statut"),
                        rs.getString("agent_username")
                ), imfId, idClient);

        List<KycResume> kycRows = jdbcTemplate.query("""
                SELECT uid, statut, niveau_actuel, niveau_risque, score_risque,
                       date_expiration_kyc, type_piece_identite, numero_piece
                FROM app.kyc_dossiers
                WHERE imf_id = ? AND client_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, (rs, n) -> {
                    UUID uid = rs.getObject("uid", UUID.class);
                    return new KycResume(
                            uid != null ? uid.toString() : null,
                            rs.getString("statut"),
                            rs.getString("niveau_actuel"),
                            rs.getString("niveau_risque"),
                            intOrNull(rs, "score_risque"),
                            dateStr(rs, "date_expiration_kyc"),
                            rs.getString("type_piece_identite"),
                            rs.getString("numero_piece")
                    );
                }, imfId, idClient);

        ClientDossierResponse full = new ClientDossierResponse(
                dossier.idClient(),
                dossier.nomClient(),
                dossier.telephoneClient(),
                dossier.telephoneSecondaire(),
                dossier.agencePrincipale(),
                dossier.zoneId(),
                dossier.actif(),
                dossier.encours(),
                dossier.maxJoursRetard(),
                dossier.statut(),
                dossier.dateNaissance(),
                dossier.sexe(),
                dossier.secteurPrincipal(),
                dossier.sousSecteur(),
                dossier.anneesExperience(),
                dossier.revenuMensuelEstime(),
                dossier.marchePrincipal(),
                dossier.frequenceMarche(),
                dossier.niveauEducation(),
                dossier.situationFamiliale(),
                dossier.nombrePersonnesCharge(),
                dossier.latitudeActivite(),
                dossier.longitudeActivite(),
                dossier.adresseActivite(),
                dossier.createdAt(),
                kycRows.isEmpty() ? null : kycRows.get(0),
                creances,
                collectes
        );
        return masquerDossierSiNecessaire(full);
    }

    public List<ClientResponse> list(int page, int size, String search, String statut, String agence) {
        List<Object> params = new ArrayList<>();
        params.add(TenantContext.currentImfId());

        StringBuilder inner = new StringBuilder("""
                SELECT c.client_id_externe AS id_client,
                       c.nom_complet       AS nom_client,
                       c.telephone_principal AS telephone_client,
                       a.nom               AS agence_principale,
                       COALESCE(cr.total_encours, 0) AS encours,
                       CASE
                           WHEN COALESCE(cr.max_retard, 0) >= 90 THEN 'DEFAILLANT'
                           WHEN COALESCE(cr.max_retard, 0) >= 30 THEN 'EN_RETARD'
                           ELSE 'ACTIF'
                       END AS statut
                FROM app.clients_informels c
                LEFT JOIN app.agences a ON a.id = c.agence_id
                LEFT JOIN (
                    SELECT client_informel_id,
                           SUM(montant_impaye) AS total_encours,
                           MAX(jours_retard)   AS max_retard
                    FROM app.creances
                    WHERE statut IN (%s)
                    GROUP BY client_informel_id
                ) cr ON cr.client_informel_id = c.id
                WHERE c.imf_id = ?
                """.formatted(CREANCES_ACTIVES));

        if (search != null && !search.isBlank()) {
            inner.append(" AND (c.nom_complet ILIKE ? OR c.telephone_principal LIKE ?)");
            params.add("%" + search.trim() + "%");
            params.add("%" + search.trim() + "%");
        }
        if (agence != null && !agence.isBlank()) {
            inner.append(" AND a.nom ILIKE ?");
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
        params.add(TenantContext.currentImfId());

        StringBuilder inner = new StringBuilder("""
                SELECT CASE
                           WHEN COALESCE(cr.max_retard, 0) >= 90 THEN 'DEFAILLANT'
                           WHEN COALESCE(cr.max_retard, 0) >= 30 THEN 'EN_RETARD'
                           ELSE 'ACTIF'
                       END AS statut
                FROM app.clients_informels c
                LEFT JOIN app.agences a ON a.id = c.agence_id
                LEFT JOIN (
                    SELECT client_informel_id, MAX(jours_retard) AS max_retard
                    FROM app.creances
                    WHERE statut IN (%s)
                    GROUP BY client_informel_id
                ) cr ON cr.client_informel_id = c.id
                WHERE c.imf_id = ?
                """.formatted(CREANCES_ACTIVES));

        if (search != null && !search.isBlank()) {
            inner.append(" AND (c.nom_complet ILIKE ? OR c.telephone_principal LIKE ?)");
            params.add("%" + search.trim() + "%");
            params.add("%" + search.trim() + "%");
        }
        if (agence != null && !agence.isBlank()) {
            inner.append(" AND a.nom ILIKE ?");
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

    private ClientDossierResponse mapDossierIdentite(ResultSet rs) throws SQLException {
        OffsetDateTime created = null;
        try {
            created = rs.getObject("created_at", OffsetDateTime.class);
        } catch (SQLException ignored) {
            var ts = rs.getTimestamp("created_at");
            if (ts != null) {
                created = ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
            }
        }
        return new ClientDossierResponse(
                rs.getString("id_client"),
                rs.getString("nom_client"),
                rs.getString("telephone_client"),
                rs.getString("telephone_secondaire"),
                rs.getString("agence_principale"),
                rs.getString("zone_id"),
                rs.getBoolean("actif"),
                decimal(rs, "encours"),
                intOrZero(rs, "max_jours_retard"),
                rs.getString("statut"),
                dateStr(rs, "date_naissance"),
                rs.getString("sexe"),
                rs.getString("secteur_principal"),
                rs.getString("sous_secteur"),
                intOrNull(rs, "annees_experience"),
                decimal(rs, "revenu_mensuel_estime"),
                rs.getString("marche_principal"),
                rs.getString("frequence_marche"),
                rs.getString("niveau_education"),
                rs.getString("situation_familiale"),
                intOrNull(rs, "nombre_personnes_charge"),
                decimal(rs, "latitude_activite"),
                decimal(rs, "longitude_activite"),
                rs.getString("adresse_activite"),
                created != null ? created.toString() : null,
                null,
                List.of(),
                List.of()
        );
    }

    private ClientDossierResponse masquerDossierSiNecessaire(ClientDossierResponse d) {
        if (d == null) return null;
        if (DataMaskingUtils.peutVoirDonneesCompletes(roleAppelant())) return d;
        KycResume kyc = d.kyc();
        KycResume kycMasque = kyc == null ? null : new KycResume(
                kyc.uid(),
                kyc.statut(),
                kyc.niveauActuel(),
                kyc.niveauRisque(),
                kyc.scoreRisque(),
                kyc.dateExpirationKyc(),
                kyc.typePieceIdentite(),
                DataMaskingUtils.masquerTelephone(kyc.numeroPiece())
        );
        return new ClientDossierResponse(
                d.idClient(),
                DataMaskingUtils.masquerNom(d.nomClient()),
                DataMaskingUtils.masquerTelephone(d.telephoneClient()),
                DataMaskingUtils.masquerTelephone(d.telephoneSecondaire()),
                d.agencePrincipale(),
                d.zoneId(),
                d.actif(),
                d.encours(),
                d.maxJoursRetard(),
                d.statut(),
                d.dateNaissance(),
                d.sexe(),
                d.secteurPrincipal(),
                d.sousSecteur(),
                d.anneesExperience(),
                d.revenuMensuelEstime(),
                d.marchePrincipal(),
                d.frequenceMarche(),
                d.niveauEducation(),
                d.situationFamiliale(),
                d.nombrePersonnesCharge(),
                null,
                null,
                null,
                d.createdAt(),
                kycMasque,
                d.creances(),
                d.collectes()
        );
    }

    private static Double decimal(ResultSet rs, String col) throws SQLException {
        BigDecimal v = rs.getBigDecimal(col);
        return v == null ? null : v.doubleValue();
    }

    private static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static int intOrZero(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? 0 : v;
    }

    private static String dateStr(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate().toString();
    }
}
