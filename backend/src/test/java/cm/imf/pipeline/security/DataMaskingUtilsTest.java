package cm.imf.pipeline.security;

import cm.imf.pipeline.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires DataMaskingUtils — conformité art. 9 et 27 Loi 2024/017.
 * Tous les masquages doivent être idempotents et lisibles par rôle privilégié.
 */
@DisplayName("DataMaskingUtils — masquage PII Loi 2024/017")
class DataMaskingUtilsTest {

    // ── masquerNom ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("masquerNom")
    class MasquerNom {

        @Test
        @DisplayName("Nom simple 'Kouam' → 'K***'")
        void nom_simple_masque() {
            assertThat(DataMaskingUtils.masquerNom("Kouam")).isEqualTo("K***");
        }

        @Test
        @DisplayName("Nom composé 'Kouam Ndjomo' → 'K*** N***'")
        void nom_compose_masque() {
            assertThat(DataMaskingUtils.masquerNom("Kouam Ndjomo")).isEqualTo("K*** N***");
        }

        @Test
        @DisplayName("Nom triple 'Jean-Pierre Fomo Martin' → masque chaque partie")
        void nom_triple_masque() {
            String result = DataMaskingUtils.masquerNom("Jean-Pierre Fomo Martin");
            assertThat(result).contains("J***").contains("F***").contains("M***");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("Null/vide/blank → retourne null")
        void nom_null_ou_vide_retourne_null(String input) {
            assertThat(DataMaskingUtils.masquerNom(input)).isNull();
        }

        @Test
        @DisplayName("Masquage ne contient jamais le nom original")
        void masque_ne_contient_pas_original() {
            String original = "Aboubakar";
            String masked = DataMaskingUtils.masquerNom(original);
            assertThat(masked).doesNotContain("boubakar");
        }
    }

    // ── masquerTelephone ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("masquerTelephone")
    class MasquerTelephone {

        @ParameterizedTest(name = "''{0}'' → ''{1}''")
        @CsvSource({
                "697112233, 697***33",
                "656001100, 656***00",
                "237697001122, 237***22",
                "+237697112233, +23***33"
        })
        @DisplayName("Masque les chiffres intermédiaires, conserve préfixe + 2 derniers")
        void telephone_masque(String input, String expected) {
            assertThat(DataMaskingUtils.masquerTelephone(input)).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Null/vide → null")
        void telephone_null_retourne_null(String input) {
            assertThat(DataMaskingUtils.masquerTelephone(input)).isNull();
        }

        @Test
        @DisplayName("Numéro trop court (< 6 chiffres) → '***'")
        void telephone_trop_court() {
            assertThat(DataMaskingUtils.masquerTelephone("123")).isEqualTo("***");
        }
    }

    // ── masquerEmail ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("masquerEmail")
    class MasquerEmail {

        @ParameterizedTest(name = "''{0}'' → ''{1}''")
        @CsvSource({
                "alice@imf.cm,   a***@imf.cm",
                "bob.smith@test.org, b***@test.org",
                "x@y.com,        x***@y.com"
        })
        @DisplayName("Masque tout sauf le premier caractère avant @ et le domaine")
        void email_masque(String input, String expected) {
            assertThat(DataMaskingUtils.masquerEmail(input.trim())).isEqualTo(expected.trim());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Null/vide → null")
        void email_null_retourne_null(String input) {
            assertThat(DataMaskingUtils.masquerEmail(input)).isNull();
        }

        @Test
        @DisplayName("Email sans @ → fallback '***@***'")
        void email_invalide_retourne_placeholder() {
            assertThat(DataMaskingUtils.masquerEmail("pasdearobase")).isEqualTo("***@***");
        }
    }

    // ── masquerNumeroCompte ───────────────────────────────────────────────────

    @Nested
    @DisplayName("masquerNumeroCompte")
    class MasquerNumeroCompte {

        @ParameterizedTest(name = "''{0}'' → ''{1}''")
        @CsvSource({
                "CM00123456, CM***56",
                "PRE-2024-001, PR***01",
                "ABC1234567, AB***67"
        })
        @DisplayName("Masque le corps, conserve 2 premiers + 2 derniers caractères")
        void numero_masque(String input, String expected) {
            assertThat(DataMaskingUtils.masquerNumeroCompte(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Numéro de 3 caractères → '***'")
        void numero_trop_court_retourne_placeholder() {
            assertThat(DataMaskingUtils.masquerNumeroCompte("AB1")).isEqualTo("***");
        }
    }

    // ── peutVoirDonneesCompletes ──────────────────────────────────────────────

    @Nested
    @DisplayName("peutVoirDonneesCompletes — accès PII par rôle")
    class AccesPii {

        @ParameterizedTest(name = "{0} peut voir les données complètes")
        @ValueSource(strings = {"RESPONSABLE_RECOUVREMENT", "DIRECTEUR", "DSI", "SUPER_ADMIN"})
        @DisplayName("Rôles privilégiés accèdent aux données PII en clair")
        void roles_privilegies(String roleName) {
            assertThat(DataMaskingUtils.peutVoirDonneesCompletes(Role.valueOf(roleName))).isTrue();
        }

        @ParameterizedTest(name = "{0} ne peut pas voir les données complètes")
        @ValueSource(strings = {"AGENT", "ANALYSTE"})
        @DisplayName("Rôles standards voient les PII masquées")
        void roles_standard(String roleName) {
            assertThat(DataMaskingUtils.peutVoirDonneesCompletes(Role.valueOf(roleName))).isFalse();
        }
    }

    // ── masquerJsonAudit ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("masquerJsonAudit — masquage champs JSONB audit")
    class MasquerJsonAudit {

        private Map<String, Object> auditData() {
            Map<String, Object> data = new HashMap<>();
            data.put("nomComplet", "Fomo Martin");
            data.put("telephone", "697112233");
            data.put("email", "fomo@imf.cm");
            data.put("montantCreance", 450000);
            data.put("statut", "EN_COURS");
            data.put("latitude", 3.8613);
            data.put("longitude", 11.5166);
            return data;
        }

        @Test
        @DisplayName("ANALYSTE → PII masquées, montants/statuts conservés")
        void analyste_voit_pii_masquees() {
            Map<String, Object> result =
                    DataMaskingUtils.masquerJsonAudit(auditData(), Role.ANALYSTE);

            assertThat(result.get("nomComplet").toString()).doesNotContain("Fomo");
            assertThat(result.get("telephone").toString()).contains("***");
            assertThat(result.get("email").toString()).contains("***");
            // Les champs non-PII restent en clair
            assertThat(result.get("montantCreance")).isEqualTo(450000);
            assertThat(result.get("statut")).isEqualTo("EN_COURS");
        }

        @Test
        @DisplayName("ANALYSTE → coordonnées GPS supprimées (RGPD)")
        void analyste_gps_supprime() {
            Map<String, Object> result =
                    DataMaskingUtils.masquerJsonAudit(auditData(), Role.ANALYSTE);

            assertThat(result).doesNotContainKey("latitude");
            assertThat(result).doesNotContainKey("longitude");
        }

        @Test
        @DisplayName("DSI → données complètes en clair (rôle privilégié)")
        void dsi_voit_donnees_completes() {
            Map<String, Object> result =
                    DataMaskingUtils.masquerJsonAudit(auditData(), Role.DSI);

            assertThat(result.get("nomComplet")).isEqualTo("Fomo Martin");
            assertThat(result.get("telephone")).isEqualTo("697112233");
        }

        @Test
        @DisplayName("null en entrée → retourne null (pas de NPE)")
        void null_retourne_null() {
            assertThat(DataMaskingUtils.masquerJsonAudit(null, Role.ANALYSTE)).isNull();
        }
    }
}
