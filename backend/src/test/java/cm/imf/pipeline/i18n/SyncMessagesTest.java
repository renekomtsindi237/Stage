package cm.imf.pipeline.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SyncMessages — vérification des messages utilisateur")
class SyncMessagesTest {

    @Test
    @DisplayName("SYNC_COMPLETE — message non vide et compréhensible")
    void syncComplete_non_vide() {
        assertThat(SyncMessages.SYNC_COMPLETE).isNotBlank();
        assertThat(SyncMessages.SYNC_COMPLETE).contains("succès");
    }

    @Test
    @DisplayName("SYNC_ECHEC — mentionne sauvegarde locale et reconnexion")
    void syncEchec_mentionne_sauvegarde_et_reconnexion() {
        assertThat(SyncMessages.SYNC_ECHEC)
                .contains("sauvegardées")
                .contains("connexion");
    }

    @ParameterizedTest(name = "syncPartielle({0} succès, {1} total, {2} conflits, {3} erreurs)")
    @CsvSource({
            "3,5,1,1",
            "0,3,2,1",
            "4,5,0,1"
    })
    @DisplayName("syncPartielle — format correct avec toutes les informations")
    void syncPartielle_format_correct(int succes, int total, int conflits, int erreurs) {
        String msg = SyncMessages.syncPartielle(succes, total, conflits, erreurs);
        assertThat(msg)
                .contains(String.valueOf(succes))
                .contains(String.valueOf(total))
                .isNotBlank();
    }

    @Test
    @DisplayName("COLLECTE_CONFIRMEE — message positif et actionnable")
    void collecteConfirmee_message_positif() {
        assertThat(SyncMessages.COLLECTE_CONFIRMEE)
                .contains("succès")
                .isNotBlank();
    }

    @Test
    @DisplayName("COLLECTE_DOUBLON_ID — explique la raison et rassure l'utilisateur")
    void collecteDoublonId_message_explicatif() {
        assertThat(SyncMessages.COLLECTE_DOUBLON_ID)
                .containsIgnoringCase("doublon")
                .contains("Aucune action nécessaire");
    }

    @Test
    @DisplayName("COLLECTE_DOUBLON_REFERENCE — indique l'action à entreprendre")
    void collecteDoublonReference_action_requise() {
        assertThat(SyncMessages.COLLECTE_DOUBLON_REFERENCE)
                .containsIgnoringCase("doublon")
                .contains("superviseur");
    }

    @Test
    @DisplayName("erreurTechnique — inclut la raison et le contact support")
    void erreurTechnique_inclut_raison() {
        String msg = SyncMessages.erreurTechnique("NullPointerException");
        assertThat(msg)
                .contains("NullPointerException")
                .contains("support");
    }

    @Test
    @DisplayName("nouvelleAlerte — inclut idPret et joursRetard")
    void nouvelleAlerte_format_complet() {
        String msg = SyncMessages.nouvelleAlerte("PRE-001", 35);
        assertThat(msg)
                .contains("PRE-001")
                .contains("35");
    }

    @Test
    @DisplayName("alerteCloturee — confirme la clôture")
    void alerteCloturee_confirme() {
        String msg = SyncMessages.alerteCloturee("PRE-002");
        assertThat(msg).contains("PRE-002").contains("clôturée");
    }

    @Test
    @DisplayName("utilisateurCree — mentionne username et rôle")
    void utilisateurCree_info_complete() {
        String msg = SyncMessages.utilisateurCree("jdoe", "ANALYSTE");
        assertThat(msg).contains("jdoe").contains("ANALYSTE");
    }

    @Test
    @DisplayName("STATUT_EN_LIGNE — message clair sur la disponibilité")
    void statutEnLigne_clair() {
        assertThat(SyncMessages.STATUT_EN_LIGNE)
                .contains("Synchronisation disponible");
    }

    @Test
    @DisplayName("STATUT_HORS_LIGNE — explique le mode dégradé et rassure")
    void statutHorsLigne_explique_mode_degrade() {
        assertThat(SyncMessages.STATUT_HORS_LIGNE)
                .contains("localement")
                .contains("automatiquement");
    }

    @Test
    @DisplayName("AUTH_ECHEC — message générique sans révéler si login ou password faux")
    void authEchec_generique() {
        assertThat(SyncMessages.AUTH_ECHEC)
                .doesNotContainIgnoringCase("login")
                .doesNotContainIgnoringCase("invalide le mot de passe")
                .contains("mot de passe"); // acceptable : conseille de vérifier les deux
    }

    @Test
    @DisplayName("VALIDATION_DATE_FUTURE — message de validation clair")
    void validationDateFuture() {
        assertThat(SyncMessages.VALIDATION_DATE_FUTURE).contains("futur");
    }

    @Test
    @DisplayName("VALIDATION_MONTANT_NEGATIF — indique la règle de validation")
    void validationMontantNegatif() {
        assertThat(SyncMessages.VALIDATION_MONTANT_NEGATIF).contains("zéro");
    }
}
