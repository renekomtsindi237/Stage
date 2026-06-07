package cm.imf.pipeline.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportService — tests CSV")
class ExportServiceTest {

    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks ExportService exportService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(exportService, "dwSchema", "dw");
        ReflectionTestUtils.setField(exportService, "stagingSchema", "staging");
    }

    @Test
    @DisplayName("exportCollectesCSV — retourne CSV avec en-tête et une ligne de données")
    void exportCollectesCSV_retourne_csv_avec_donnees() {
        Map<String, Object> row = Map.of(
                "date_collecte", "2024-01-15",
                "canal", "MTN",
                "nom_agence", "Agence Nord",
                "nom_client", "Jean Dupont",
                "reference_transaction", "REF001",
                "montant", 25000,
                "statut", "CONFIRMEE",
                "nom_fichier_source", "mtn_20240115.csv"
        );
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(row));

        String csv = exportService.exportCollectesCSV(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        assertThat(csv).contains("date_collecte;canal;agence;client;reference;montant;statut;fichier_source");
        assertThat(csv).contains("2024-01-15;MTN;Agence Nord;Jean Dupont;REF001;25000;CONFIRMEE;mtn_20240115.csv");
    }

    @Test
    @DisplayName("exportCollectesCSV — aucune donnée → seulement l'en-tête")
    void exportCollectesCSV_vide_retourne_entete_seul() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        String csv = exportService.exportCollectesCSV(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(1);
        assertThat(lines[0]).startsWith("date_collecte");
    }

    @Test
    @DisplayName("exportCollectesCSV — valeur avec point-virgule → échappe correctement")
    void exportCollectesCSV_echappe_point_virgule() {
        Map<String, Object> row = Map.of(
                "date_collecte", "2024-01-15",
                "canal", "MTN",
                "nom_agence", "Agence; Nord",   // point-virgule dans le nom
                "nom_client", "Dupont",
                "reference_transaction", "REF001",
                "montant", 25000,
                "statut", "CONFIRMEE",
                "nom_fichier_source", "file.csv"
        );
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        String csv = exportService.exportCollectesCSV(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

        // Le ';' dans le nom d'agence doit être remplacé par ','
        assertThat(csv).contains("Agence, Nord");
        assertThat(csv).doesNotContain("Agence; Nord");
    }

    @Test
    @DisplayName("exportPretsEnRetardCSV — retourne CSV prêts en retard")
    void exportPretsEnRetardCSV_retourne_csv() {
        Map<String, Object> row = Map.of(
                "id_pret", "PRE-001",
                "id_client", "CLI-001",
                "nom_client", "Marie Kamga",
                "nom_agence", "Agence Est",
                "nom_produit", "Crédit Solidaire",
                "montant_pret", 500000,
                "solde_restant", 350000,
                "statut_pret", "EN_RETARD",
                "jours_retard", 45
        );
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(row));

        String csv = exportService.exportPretsEnRetardCSV();

        assertThat(csv).contains("id_pret;id_client;nom_client");
        assertThat(csv).contains("PRE-001;CLI-001;Marie Kamga;Agence Est;Crédit Solidaire;500000;350000;EN_RETARD;45");
    }

    @Test
    @DisplayName("exportPretsEnRetardCSV — valeurs nulles → cellule vide")
    void exportPretsEnRetardCSV_valeur_null_vide() {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id_pret", "PRE-002");
        row.put("id_client", null);
        row.put("nom_client", null);
        row.put("nom_agence", "Agence Sud");
        row.put("nom_produit", null);
        row.put("montant_pret", null);
        row.put("solde_restant", null);
        row.put("statut_pret", "EN_RETARD");
        row.put("jours_retard", 10);

        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(row));

        String csv = exportService.exportPretsEnRetardCSV();

        assertThat(csv).contains("PRE-002;;");  // champs nulls → vides
    }
}
