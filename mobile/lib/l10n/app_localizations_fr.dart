// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for French (`fr`).
class AppL10nFr extends AppL10n {
  AppL10nFr([String locale = 'fr']) : super(locale);

  @override
  String get appName => 'MicroRecouv';

  @override
  String get appTagline => 'Gestion & Recouvrement Intelligents';

  @override
  String get appLoading => 'Chargement...';

  @override
  String get appVersion => 'v1.0.0';

  @override
  String get appImfCameroun => 'IMF Cameroun';

  @override
  String get cancel => 'Annuler';

  @override
  String get retry => 'Réessayer';

  @override
  String get backHome => 'Retour à l\'accueil';

  @override
  String get error => 'Erreur';

  @override
  String get confirm => 'Confirmer';

  @override
  String get close => 'Fermer';

  @override
  String get save => 'Enregistrer';

  @override
  String get send => 'Envoyer';

  @override
  String get refresh => 'Actualiser';

  @override
  String get currencyFcfa => 'FCFA';

  @override
  String get currencyF => 'F';

  @override
  String get loginTitle => 'Connexion';

  @override
  String get loginSubtitle =>
      'Un code de vérification sera envoyé à votre adresse email';

  @override
  String get loginEmailLabel => 'Adresse email professionnelle';

  @override
  String get loginEmailHint => 'votre@institution.cm ou exemple@gmail.com';

  @override
  String get loginEmailRequired => 'Email requis';

  @override
  String get loginEmailInvalid => 'Email invalide';

  @override
  String get loginSubmitButton => 'Recevoir mon code';

  @override
  String get loginFooterHelp =>
      'Problème de connexion ? Contacter l\'administrateur';

  @override
  String get loginNetworkError => 'Erreur réseau';

  @override
  String get loginBackHome => 'Accueil';

  @override
  String get otpTitle => 'Vérification OTP';

  @override
  String get otpSubtitlePrefix => 'Code à 6 chiffres envoyé à ';

  @override
  String get otpCodeLabel => 'Code de vérification';

  @override
  String get otpVerifyButton => 'Vérifier le code';

  @override
  String get otpResendPrompt => 'Code non reçu ? ';

  @override
  String get otpResendAction => 'Renvoyer';

  @override
  String otpResendCountdown(int seconds) {
    return 'Renvoyer dans ${seconds}s';
  }

  @override
  String get otpBackToLogin => '← Retour à la connexion';

  @override
  String get landingBadge => 'PLATEFORME DE MICROFINANCE — CAMEROUN';

  @override
  String get landingTitle => 'Gérez vos crédits simplement et en sécurité';

  @override
  String get landingSubtitle =>
      'MicroRecouv est une plateforme numérique qui aide les institutions de microfinance à suivre leurs clients, gérer les remboursements et recouvrer les crédits en retard.';

  @override
  String get landingCardTitle => 'Accès personnel';

  @override
  String get landingCardDescription =>
      'Pour les agents, directeurs, caissiers et toute l\'équipe de l\'institution.';

  @override
  String get landingCheckEmailCode => 'Connexion par code email';

  @override
  String get landingCheckNoPassword => 'Aucun mot de passe à retenir';

  @override
  String get landingCtaButton => 'Se connecter';

  @override
  String get dashboardUserFallback => 'Utilisateur';

  @override
  String get dashboardSyncInProgress => 'Synchronisation en cours…';

  @override
  String dashboardSyncPending(int count) {
    return '$count collecte(s) en attente de sync';
  }

  @override
  String get dashboardSyncOk => 'Synchronisé à l\'instant';

  @override
  String get dashboardActivityTitle => 'Activité du jour';

  @override
  String get dashboardCollectedSuffix => 'collecté aujourd\'hui';

  @override
  String get dashboardStatClientsVisited => 'Clients visités';

  @override
  String get dashboardStatCollectes => 'Collectes';

  @override
  String get dashboardQuickActionCollecte => 'Nouvelle\nCollecte';

  @override
  String get dashboardQuickActionClients => 'Mes\nClients';

  @override
  String get dashboardAlertesSectionTitle => 'Alertes sur vos clients';

  @override
  String get dashboardAlertesError => 'Impossible de charger les alertes';

  @override
  String get dashboardAlertesEmpty => 'Aucune alerte active';

  @override
  String get dashboardAlerteCritique => 'CRITIQUE';

  @override
  String get dashboardAlerteActive => 'ACTIVE';

  @override
  String get profilTitle => 'Mon Profil';

  @override
  String get profilUserFallback => 'Utilisateur';

  @override
  String get profilRoleFallback => 'Agent Terrain';

  @override
  String get profilAvatarDelete => 'Supprimer la photo';

  @override
  String get profilAvatarUpdated => 'Photo de profil mise à jour';

  @override
  String get profilAvatarDeleted => 'Photo supprimée';

  @override
  String get profilSectionGps => 'Localisation GPS';

  @override
  String get profilSectionAppearance => 'Apparence';

  @override
  String get profilSectionAccount => 'Compte';

  @override
  String get profilSectionSession => 'Session';

  @override
  String sessionValidUntil(String time) {
    return 'Valable jusqu\'au $time';
  }

  @override
  String get sessionExpired => 'Session de 24 h expirée. Reconnectez-vous.';

  @override
  String get sessionDurationHint =>
      '24 h à partir de l\'heure de connexion (horodatage terrain).';

  @override
  String get profilGpsActive => 'Actif';

  @override
  String get profilGpsOffline => 'Hors-ligne (positions en file)';

  @override
  String get profilGpsError => 'Erreur';

  @override
  String get profilGpsIdle => 'Inactif';

  @override
  String get profilGpsRequired => 'Obligatoire';

  @override
  String get profilGpsCoordinates => 'Coordonnées';

  @override
  String get profilGpsAccuracy => 'Précision';

  @override
  String get profilGpsSpeed => 'Vitesse';

  @override
  String profilGpsPendingPositions(int count) {
    return '$count position(s) en attente de synchronisation';
  }

  @override
  String get profilGpsBtnStop => 'Arrêter le partage GPS';

  @override
  String get profilGpsBtnStopRequired => 'GPS requis par votre IMF';

  @override
  String get profilGpsBtnStopOffline => 'Arrêt impossible hors-ligne';

  @override
  String get profilGpsBtnStart => 'Démarrer le partage GPS';

  @override
  String get profilGpsSnackbarCantStop =>
      'Impossible d\'arrêter le GPS : votre IMF exige le partage de position.';

  @override
  String get profilThemeDarkMode => 'Mode sombre';

  @override
  String get profilLogoutLabel => 'Se déconnecter';

  @override
  String get profilDialogLogoutTitle => 'Déconnexion';

  @override
  String get profilDialogLogoutContent =>
      'Êtes-vous sûr de vouloir vous déconnecter ?';

  @override
  String get profilDialogConfirmLogout => 'Déconnecter';

  @override
  String get alertesTitle => 'Alertes';

  @override
  String get alertesFilterAll => 'Toutes';

  @override
  String get alertesFilterActive => 'Actives';

  @override
  String get alertesFilterEscalated => 'Escaladées';

  @override
  String get alertesFilterTreated => 'Traitées';

  @override
  String get alertesFilterClosed => 'Clôturées';

  @override
  String get alertesLoadError => 'Impossible de charger les alertes';

  @override
  String get alertesEmpty => 'Aucune alerte';

  @override
  String get alertesClientUnknown => 'Client inconnu';

  @override
  String alertesDelayBadge(int days) {
    return '${days}j retard';
  }

  @override
  String alertesAmountDue(String amount) {
    return '$amount FCFA dû';
  }

  @override
  String get clientsTitle => 'Clients';

  @override
  String get clientsSearchHint => 'Rechercher un client...';

  @override
  String get clientsEmptyTitle => 'Aucun client trouvé';

  @override
  String get clientsEmptySubtitle => 'Essayez une autre recherche';

  @override
  String get clientDetailTitle => 'Détail client';

  @override
  String get nouvelleCollecteTitle => 'Enregistrer une collecte';

  @override
  String get nouvelleCollecteSearchHint =>
      'Rechercher un client (nom, téléphone…)';

  @override
  String get nouvelleCollecteChangeClient => 'Changer';

  @override
  String get nouvelleCollecteAmountLabel => 'Montant';

  @override
  String get nouvelleCollecteCanalLabel => 'Canal de paiement';

  @override
  String get nouvelleCollecteSubmit => 'Enregistrer la collecte';

  @override
  String get nouvelleCollecteNoClient => 'Veuillez sélectionner un client';

  @override
  String get nouvelleCollecteInvalidAmount => 'Montant invalide';

  @override
  String get confirmationTitle => 'Collecte enregistrée';

  @override
  String get confirmationSuccessTitle => 'Collecte sauvegardée !';

  @override
  String get confirmationSuccessSubtitle =>
      'Elle sera synchronisée dès que vous serez connecté.';

  @override
  String get confirmationAmount => 'Montant';

  @override
  String get confirmationClientId => 'Client ID';

  @override
  String get confirmationCanal => 'Canal';

  @override
  String get confirmationDate => 'Date';

  @override
  String get confirmationGps => 'GPS';

  @override
  String get confirmationPendingSync => 'En attente de synchronisation';

  @override
  String get confirmationNewCollecte => 'Nouvelle collecte';

  @override
  String get historiqueTitle => 'Historique du jour';

  @override
  String get historiquePendingLabel => 'En attente';

  @override
  String get historiqueTotalAmount => 'Montant total';

  @override
  String get historiqueSyncing => 'Synchronisation...';

  @override
  String get historiqueSyncNow => 'Synchroniser maintenant';

  @override
  String historiqueLastSync(int accepted, int total, String time) {
    return 'Dernière sync : $accepted/$total — $time';
  }

  @override
  String get historiqueEmptyTitle => 'Aucune collecte en attente';

  @override
  String get historiqueEmptySubtitle =>
      'Toutes vos collectes sont synchronisées.';

  @override
  String get historiqueSyncedTitle => 'Déjà envoyées au serveur';

  @override
  String historiqueListTitle(int n) {
    return 'Collectes ($n)';
  }

  @override
  String get syncResultTitle => 'Synchronisation terminée';

  @override
  String get syncResultTotal => 'Total reçu';

  @override
  String get syncResultAccepted => 'Acceptées';

  @override
  String get syncResultDuplicates => 'Doublons';

  @override
  String get syncResultRejected => 'Rejetées';

  @override
  String syncResultRejectedWarning(int count) {
    return '$count collecte(s) non synchronisée(s). Contactez le support.';
  }

  @override
  String get errorTitle => 'Une erreur est survenue';

  @override
  String errorCodeBadge(String code) {
    return 'Erreur $code';
  }

  @override
  String get errorDefaultMessage =>
      'Quelque chose s\'est mal passé. Veuillez réessayer ou revenir à l\'accueil.';

  @override
  String get offlineTitle => 'Connexion indisponible';

  @override
  String get offlineDescription =>
      'Vérifiez votre connexion Wi-Fi ou données mobiles.\nClients, collectes et tableau de bord restent disponibles depuis le cache local (72 h).';

  @override
  String get offlineBanner =>
      'Hors ligne — clients et collectes depuis l\'appareil';

  @override
  String get offlineCachedChip => 'Données locales';

  @override
  String get offlineNoClientsCache =>
      'Aucun client en cache. Connectez-vous une fois au réseau pour télécharger le portefeuille.';

  @override
  String get serverSection => 'Serveur';

  @override
  String get serverProduction => 'En ligne (prod)';

  @override
  String get serverStaging => 'Staging';

  @override
  String get serverLocal => 'Serveur local';

  @override
  String get serverSwitchTitle => 'Changer de serveur ?';

  @override
  String get serverSwitchContent =>
      'Les collectes en attente restent sur l\'appareil et seront envoyées au nouveau serveur après reconnexion. Le cache clients sera retéléchargé.';

  @override
  String serverSwitchPending(int count) {
    return '$count collecte(s) encore locales seront transférées.';
  }

  @override
  String get serverSwitchConfirm => 'Changer et se reconnecter';

  @override
  String serverCurrent(String url) {
    return 'Actuel : $url';
  }

  @override
  String get offlineBadge => 'Vos collectes sont sauvegardées localement';

  @override
  String get offlineChecking => 'Vérification...';

  @override
  String get offlineCheckButton => 'Vérifier la connexion';

  @override
  String get offlineStillOffline => 'Toujours pas de connexion';

  @override
  String get langFr => 'Français';

  @override
  String get langEn => 'English';

  @override
  String get langSwitchTooltip => 'Changer la langue';

  @override
  String get navHome => 'Accueil';

  @override
  String get navClients => 'Clients';

  @override
  String get navHistorique => 'Historique';

  @override
  String errorWithDetail(String detail) {
    return 'Erreur : $detail';
  }
}
