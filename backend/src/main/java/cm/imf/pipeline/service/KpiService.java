package cm.imf.pipeline.service;

import cm.imf.pipeline.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KpiService implements IKpiService {

    private final JdbcTemplate jdbcTemplate;
    private final IAlertService alerteService;

    @Value("${imf.pipeline.dw-schema:dw}")
    private String dwSchema;

    @Value("${imf.pipeline.staging-schema:staging}")
    private String stagingSchema;

    @Cacheable(value = "kpi-par",
               key = "T(cm.imf.pipeline.security.TenantContext).currentImfId() + '_' + #dateDebut + '_' + #dateFin")
    public List<Map<String, Object>> getParStats(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
                SELECT
                    da.nom_agence,
                    d.date_valeur,
                    fr.montant_attendu  AS montant_pret,
                    fr.montant_rembourse,
                    fr.solde_restant,
                    fr.statut_pret,
                    fr.jours_retard,
                    CASE WHEN fr.jours_retard >=  30 THEN fr.solde_restant ELSE 0 END AS encours_par30,
                    CASE WHEN fr.jours_retard >=  60 THEN fr.solde_restant ELSE 0 END AS encours_par60,
                    CASE WHEN fr.jours_retard >=  90 THEN fr.solde_restant ELSE 0 END AS encours_par90,
                    CASE WHEN fr.jours_retard >= 180 THEN fr.solde_restant ELSE 0 END AS encours_par180,
                    CASE WHEN fr.jours_retard >= 365 THEN fr.solde_restant ELSE 0 END AS encours_par365
                FROM %s.fact_remboursements fr
                JOIN %s.dim_date d ON fr.date_key = d.date_key
                LEFT JOIN %s.dim_agence da ON fr.agence_key = da.agence_key
                WHERE d.date_valeur BETWEEN ?::date AND ?::date
                ORDER BY d.date_valeur, da.nom_agence
                """.formatted(dwSchema, dwSchema, dwSchema);

        return jdbcTemplate.queryForList(sql,
                dateDebut.format(DateTimeFormatter.ISO_DATE),
                dateFin.format(DateTimeFormatter.ISO_DATE));
    }

    @Cacheable(value = "kpi-collectes",
               key = "T(cm.imf.pipeline.security.TenantContext).currentImfId() + '_' + #dateDebut + '_' + #dateFin")
    public List<Map<String, Object>> getCollecteStats(LocalDate dateDebut, LocalDate dateFin) {
        String sql = """
                SELECT
                    d.date_valeur,
                    fc.canal,
                    da.nom_agence,
                    COUNT(*)           AS nb_collectes,
                    SUM(fc.montant)    AS montant_total
                FROM %s.fact_collectes fc
                JOIN %s.dim_date d ON fc.date_key = d.date_key
                LEFT JOIN %s.dim_agence da ON fc.agence_key = da.agence_key
                WHERE d.date_valeur BETWEEN ?::date AND ?::date
                GROUP BY d.date_valeur, fc.canal, da.nom_agence
                ORDER BY d.date_valeur, montant_total DESC
                """.formatted(dwSchema, dwSchema, dwSchema);

        return jdbcTemplate.queryForList(sql,
                dateDebut.format(DateTimeFormatter.ISO_DATE),
                dateFin.format(DateTimeFormatter.ISO_DATE));
    }

    @Cacheable(value = "kpi-dashboard",
               key = "T(cm.imf.pipeline.security.TenantContext).currentImfId() ?: 'global'")
    public Map<String, Object> getDashboardSummary() {
        LocalDate fin   = LocalDate.now();
        LocalDate debut = fin.minusDays(30);

        String sqlCollectes = """
                SELECT COALESCE(SUM(fc.montant), 0) AS total_collectes,
                       COUNT(*)                      AS nb_collectes
                FROM %s.fact_collectes fc
                JOIN %s.dim_date d ON fc.date_key = d.date_key
                WHERE d.date_valeur BETWEEN ?::date AND ?::date
                """.formatted(dwSchema, dwSchema);

        String sqlPar = """
                SELECT
                    COALESCE(SUM(CASE WHEN fr.jours_retard >=  30 THEN fr.solde_restant ELSE 0 END), 0) AS encours_par30,
                    COALESCE(SUM(CASE WHEN fr.jours_retard >=  60 THEN fr.solde_restant ELSE 0 END), 0) AS encours_par60,
                    COALESCE(SUM(CASE WHEN fr.jours_retard >=  90 THEN fr.solde_restant ELSE 0 END), 0) AS encours_par90,
                    COALESCE(SUM(CASE WHEN fr.jours_retard >= 180 THEN fr.solde_restant ELSE 0 END), 0) AS encours_par180,
                    COALESCE(SUM(CASE WHEN fr.jours_retard >= 365 THEN fr.solde_restant ELSE 0 END), 0) AS encours_par365,
                    COALESCE(SUM(fr.montant_attendu), 0) AS encours_total
                FROM %s.fact_remboursements fr
                JOIN %s.dim_date d ON fr.date_key = d.date_key
                WHERE d.date_valeur = ?::date
                  AND fr.statut_pret NOT IN ('SOLDE', 'PERTE')
                """.formatted(dwSchema, dwSchema);

        String debutStr = debut.format(DateTimeFormatter.ISO_DATE);
        String finStr   = fin.format(DateTimeFormatter.ISO_DATE);

        Map<String, Object> collectesRow = jdbcTemplate.queryForMap(sqlCollectes, debutStr, finStr);
        Map<String, Object> parRow       = jdbcTemplate.queryForMap(sqlPar, finStr);
        long nbAlertesActives            = alerteService.countActiveAlertes();

        return Map.ofEntries(
                Map.entry("totalCollectes",   collectesRow.get("total_collectes")),
                Map.entry("nbCollectes",      collectesRow.get("nb_collectes")),
                Map.entry("encoursPar30",     parRow.get("encours_par30")),
                Map.entry("encoursPar60",     parRow.get("encours_par60")),
                Map.entry("encoursPar90",     parRow.get("encours_par90")),
                Map.entry("encoursPar180",    parRow.get("encours_par180")),
                Map.entry("encoursPar365",    parRow.get("encours_par365")),
                Map.entry("encoursTotal",     parRow.get("encours_total")),
                Map.entry("nbAlertesActives", nbAlertesActives),
                Map.entry("dateDebut",        debutStr),
                Map.entry("dateFin",          finStr)
        );
    }
}
