package cm.imf.pipeline.i18n;

/**
 * Centralization of all user-facing messages in French.
 * Covers: sync success/failure, offline conflicts, online status, validation errors.
 *
 * Usage: SyncMessages.COLLECTE_CONFIRMEE
 * Result: "Collecte enregistrée avec succès."
 */
public final class SyncMessages {

    private SyncMessages() {}

    // ── Synchronisation générale ──────────────────────────────────────────────

    /** Sync complète — tous les éléments traités avec succès. */
    public static final String SYNC_COMPLETE =
            "Synchronisation complète : toutes les collectes ont été enregistrées avec succès.";

    /** Sync partielle — certains éléments ont échoué ou sont en conflit. */
    public static String syncPartielle(int succes, int total, int conflits, int erreurs) {
        return String.format(
                "Synchronisation partielle : %d/%d collecte(s) enregistrée(s)." +
                " %d conflit(s) détecté(s), %d erreur(s) technique(s).",
                succes, total, conflits, erreurs);
    }

    /** Sync échouée totalement. */
    public static final String SYNC_ECHEC =
            "Synchronisation échouée. Vos collectes restent sauvegardées sur l'appareil." +
            " Réessayez lorsque vous avez une connexion stable.";

    /** Aucune collecte à synchroniser. */
    public static final String SYNC_VIDE =
            "Aucune collecte en attente de synchronisation.";

    // ── Résultats par collecte ────────────────────────────────────────────────

    /** Collecte traitée avec succès. */
    public static final String COLLECTE_CONFIRMEE =
            "Collecte enregistrée avec succès.";

    /** Collecte déjà connue du serveur (même ID mobile). */
    public static final String COLLECTE_DOUBLON_ID =
            "Doublon détecté : cette collecte a déjà été reçue par le serveur (même identifiant mobile)." +
            " Aucune action nécessaire.";

    /** Collecte en doublon de référence de transaction. */
    public static final String COLLECTE_DOUBLON_REFERENCE =
            "Doublon détecté : une collecte avec la même référence de transaction existe déjà" +
            " pour cette date. Vérifiez auprès de votre superviseur.";

    /** Conflit : prêt clôturé entre la saisie hors-ligne et la synchronisation. */
    public static final String COLLECTE_CONFLIT_PRET_CLOTURE =
            "Conflit de synchronisation : le prêt associé a été clôturé pendant votre période" +
            " hors-ligne. La collecte a été marquée 'REJETEE'. Contactez votre responsable.";

    /** Conflit : montant incohérent avec le solde restant. */
    public static String conflitMontant(String idPret, String soldeRestant) {
        return String.format(
                "Conflit de synchronisation sur le prêt %s : le montant saisi (%.2f) dépasse le" +
                " solde restant (%s). La collecte est mise en attente de validation.",
                idPret, 0.0, soldeRestant);
    }

    /** Collecte rejetée pour validation en attente. */
    public static final String COLLECTE_EN_ATTENTE_VALIDATION =
            "Collecte mise en attente de validation par un superviseur.";

    /** Erreur technique sur un item de synchronisation. */
    public static String erreurTechnique(String raison) {
        return "Erreur technique : " + raison +
               ". Cette collecte n'a pas pu être enregistrée. Réessayez ou contactez le support.";
    }

    // ── Connexion / Hors-ligne ────────────────────────────────────────────────

    /** Serveur accessible — en ligne. */
    public static final String STATUT_EN_LIGNE =
            "Connexion au serveur établie. Synchronisation disponible.";

    /** Serveur inaccessible — hors-ligne. */
    public static final String STATUT_HORS_LIGNE =
            "Serveur inaccessible. Vos collectes sont sauvegardées localement" +
            " et seront synchronisées automatiquement dès le retour de la connexion.";

    /** Première synchronisation après reconnexion. */
    public static final String SYNC_RECONNEXION =
            "Connexion rétablie. Synchronisation de vos données en cours…";

    // ── Alertes temps réel ────────────────────────────────────────────────────

    /** Nouvelle alerte impayé. */
    public static String nouvelleAlerte(String idPret, int joursRetard) {
        return String.format(
                "Nouvelle alerte impayé : prêt %s en retard de %d jour(s). Action requise.",
                idPret, joursRetard);
    }

    /** Alerte clôturée. */
    public static String alerteCloturee(String idPret) {
        return String.format("Alerte sur le prêt %s clôturée avec succès.", idPret);
    }

    /** Alerte escaladée. */
    public static String alerteEscaladee(String idPret) {
        return String.format("Alerte sur le prêt %s escaladée au responsable hiérarchique.", idPret);
    }

    /** KPI mis à jour après exécution du pipeline. */
    public static final String KPI_UPDATED =
            "Tableau de bord mis à jour. Données rafraîchies depuis le pipeline.";

    /** Statut pipeline DAG. */
    public static String pipelineTermine(String dagId) {
        return String.format("Pipeline '%s' terminé. Les données sont à jour.", dagId);
    }

    public static String pipelineEchec(String dagId) {
        return String.format(
                "Pipeline '%s' en erreur. Données potentiellement incomplètes." +
                " Le DSI a été notifié.", dagId);
    }

    // ── Authentification / Autorisation ──────────────────────────────────────

    public static final String AUTH_SUCCES =
            "Connexion réussie. Bienvenue sur la plateforme IMF.";

    public static final String AUTH_ECHEC =
            "Identifiants invalides. Vérifiez votre nom d'utilisateur et mot de passe.";

    public static final String AUTH_COMPTE_DESACTIVE =
            "Votre compte est désactivé. Contactez le DSI pour le réactiver.";

    public static final String AUTH_TOKEN_EXPIRE =
            "Votre session a expiré. Veuillez vous reconnecter.";

    public static final String AUTH_ACCES_REFUSE =
            "Accès refusé : votre rôle ne permet pas cette action.";

    // ── Validation des données ────────────────────────────────────────────────

    public static final String VALIDATION_MONTANT_NEGATIF =
            "Le montant de la collecte doit être supérieur à zéro.";

    public static final String VALIDATION_DATE_FUTURE =
            "La date de collecte ne peut pas être dans le futur.";

    public static final String VALIDATION_CHAMPS_OBLIGATOIRES =
            "Des champs obligatoires sont manquants. Vérifiez votre formulaire.";

    public static String validationChampManquant(String champ) {
        return String.format("Le champ '%s' est obligatoire.", champ);
    }

    // ── Administration ────────────────────────────────────────────────────────

    public static String utilisateurCree(String username, String role) {
        return String.format("Utilisateur '%s' créé avec le rôle %s.", username, role);
    }

    public static String utilisateurDesactive(String username) {
        return String.format("Compte de '%s' désactivé. L'utilisateur ne peut plus se connecter.", username);
    }

    public static String utilisateurReactive(String username) {
        return String.format("Compte de '%s' réactivé. L'utilisateur peut se reconnecter.", username);
    }

    public static String motDePasseReinitialise(String username) {
        return String.format("Mot de passe de '%s' réinitialisé. L'utilisateur doit se reconnecter.", username);
    }

    public static String usernameDejaUtilise(String username) {
        return String.format("Le nom d'utilisateur '%s' est déjà utilisé. Choisissez un autre identifiant.", username);
    }

    // ── Export / Rapport ─────────────────────────────────────────────────────

    public static String exportCsvPret(int nb) {
        return String.format("Export CSV généré : %d prêt(s) en retard.", nb);
    }

    public static String exportCsvCollecte(int nb) {
        return String.format("Export CSV généré : %d collecte(s) pour la période sélectionnée.", nb);
    }

    public static String exportPdfGenere(String type) {
        return String.format("Rapport PDF '%s' généré et prêt au téléchargement.", type);
    }

    public static final String EXPORT_PERIODE_VIDE =
            "Aucune donnée disponible pour la période sélectionnée." +
            " Vérifiez les dates et réessayez.";
}
