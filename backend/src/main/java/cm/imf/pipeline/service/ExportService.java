package cm.imf.pipeline.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExportService implements IExportService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${imf.pipeline.dw-schema:dw}")
    private String dwSchema;

    @Value("${imf.pipeline.staging-schema:staging}")
    private String stagingSchema;

    /**
     * Export CSV des collectes pour une période donnée.
     * Retourne le contenu CSV en String (à streamer dans la réponse HTTP).
     */
    public String exportCollectesCSV(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
                SELECT
                    d.date_valeur        AS date_collecte,
                    fc.canal,
                    da.nom_agence,
                    dc.nom_client,
                    fc.reference_transaction,
                    fc.montant,
                    fc.statut,
                    fc.nom_fichier_source
                FROM %s.fact_collectes fc
                JOIN %s.dim_date d   ON fc.date_key = d.date_key
                JOIN %s.dim_agence da ON fc.id_agence = da.id_agence
                LEFT JOIN %s.dim_client dc ON fc.id_client_source = dc.id_client_source
                WHERE d.date_valeur BETWEEN ? AND ?
                ORDER BY d.date_valeur, da.nom_agence
                """.formatted(dwSchema, dwSchema, dwSchema, dwSchema);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                dateDebut.format(DateTimeFormatter.ISO_DATE),
                dateFin.format(DateTimeFormatter.ISO_DATE));

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println("date_collecte;canal;agence;client;reference;montant;statut;fichier_source");
        for (Map<String, Object> row : rows) {
            pw.printf("%s;%s;%s;%s;%s;%s;%s;%s%n",
                    safe(row, "date_collecte"),
                    safe(row, "canal"),
                    safe(row, "nom_agence"),
                    safe(row, "nom_client"),
                    safe(row, "reference_transaction"),
                    safe(row, "montant"),
                    safe(row, "statut"),
                    safe(row, "nom_fichier_source"));
        }
        return sw.toString();
    }

    /**
     * Export CSV des prêts en retard (PAR).
     */
    public String exportPretsEnRetardCSV() {
        String sql = """
                SELECT id_pret, id_client, nom_client, nom_agence, nom_produit,
                       montant_pret, solde_restant, statut_pret, jours_retard
                FROM %s.stg_prets
                WHERE statut_pret IN ('EN_RETARD', 'EN_RECOUVREMENT')
                ORDER BY jours_retard DESC
                """.formatted(stagingSchema);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println("id_pret;id_client;nom_client;agence;produit;montant_pret;solde_restant;statut;jours_retard");
        for (Map<String, Object> row : rows) {
            pw.printf("%s;%s;%s;%s;%s;%s;%s;%s;%s%n",
                    safe(row, "id_pret"), safe(row, "id_client"), safe(row, "nom_client"),
                    safe(row, "nom_agence"), safe(row, "nom_produit"), safe(row, "montant_pret"),
                    safe(row, "solde_restant"), safe(row, "statut_pret"), safe(row, "jours_retard"));
        }
        return sw.toString();
    }

    private String safe(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return "";
        // Escaper les point-virgules pour ne pas casser le CSV
        return v.toString().replace(";", ",");
    }
}
