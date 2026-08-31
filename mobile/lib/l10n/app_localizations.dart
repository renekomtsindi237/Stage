import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_fr.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppL10n
/// returned by `AppL10n.of(context)`.
///
/// Applications need to include `AppL10n.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppL10n.localizationsDelegates,
///   supportedLocales: AppL10n.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppL10n.supportedLocales
/// property.
abstract class AppL10n {
  AppL10n(String locale)
      : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppL10n of(BuildContext context) {
    return Localizations.of<AppL10n>(context, AppL10n)!;
  }

  static const LocalizationsDelegate<AppL10n> delegate = _AppL10nDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('fr')
  ];

  /// No description provided for @appName.
  ///
  /// In fr, this message translates to:
  /// **'MicroRecouv'**
  String get appName;

  /// No description provided for @appTagline.
  ///
  /// In fr, this message translates to:
  /// **'Gestion & Recouvrement Intelligents'**
  String get appTagline;

  /// No description provided for @appLoading.
  ///
  /// In fr, this message translates to:
  /// **'Chargement...'**
  String get appLoading;

  /// No description provided for @appVersion.
  ///
  /// In fr, this message translates to:
  /// **'v1.0.0'**
  String get appVersion;

  /// No description provided for @appImfCameroun.
  ///
  /// In fr, this message translates to:
  /// **'IMF Cameroun'**
  String get appImfCameroun;

  /// No description provided for @cancel.
  ///
  /// In fr, this message translates to:
  /// **'Annuler'**
  String get cancel;

  /// No description provided for @retry.
  ///
  /// In fr, this message translates to:
  /// **'Réessayer'**
  String get retry;

  /// No description provided for @backHome.
  ///
  /// In fr, this message translates to:
  /// **'Retour à l\'accueil'**
  String get backHome;

  /// No description provided for @error.
  ///
  /// In fr, this message translates to:
  /// **'Erreur'**
  String get error;

  /// No description provided for @confirm.
  ///
  /// In fr, this message translates to:
  /// **'Confirmer'**
  String get confirm;

  /// No description provided for @close.
  ///
  /// In fr, this message translates to:
  /// **'Fermer'**
  String get close;

  /// No description provided for @save.
  ///
  /// In fr, this message translates to:
  /// **'Enregistrer'**
  String get save;

  /// No description provided for @send.
  ///
  /// In fr, this message translates to:
  /// **'Envoyer'**
  String get send;

  /// No description provided for @refresh.
  ///
  /// In fr, this message translates to:
  /// **'Actualiser'**
  String get refresh;

  /// No description provided for @currencyFcfa.
  ///
  /// In fr, this message translates to:
  /// **'FCFA'**
  String get currencyFcfa;

  /// No description provided for @currencyF.
  ///
  /// In fr, this message translates to:
  /// **'F'**
  String get currencyF;

  /// No description provided for @loginTitle.
  ///
  /// In fr, this message translates to:
  /// **'Connexion'**
  String get loginTitle;

  /// No description provided for @loginSubtitle.
  ///
  /// In fr, this message translates to:
  /// **'Un code de vérification sera envoyé à votre adresse email'**
  String get loginSubtitle;

  /// No description provided for @loginEmailLabel.
  ///
  /// In fr, this message translates to:
  /// **'Adresse email professionnelle'**
  String get loginEmailLabel;

  /// No description provided for @loginEmailHint.
  ///
  /// In fr, this message translates to:
  /// **'votre@institution.cm ou exemple@gmail.com'**
  String get loginEmailHint;

  /// No description provided for @loginEmailRequired.
  ///
  /// In fr, this message translates to:
  /// **'Email requis'**
  String get loginEmailRequired;

  /// No description provided for @loginEmailInvalid.
  ///
  /// In fr, this message translates to:
  /// **'Email invalide'**
  String get loginEmailInvalid;

  /// No description provided for @loginSubmitButton.
  ///
  /// In fr, this message translates to:
  /// **'Recevoir mon code'**
  String get loginSubmitButton;

  /// No description provided for @loginFooterHelp.
  ///
  /// In fr, this message translates to:
  /// **'Problème de connexion ? Contacter l\'administrateur'**
  String get loginFooterHelp;

  /// No description provided for @loginNetworkError.
  ///
  /// In fr, this message translates to:
  /// **'Erreur réseau'**
  String get loginNetworkError;

  /// No description provided for @loginBackHome.
  ///
  /// In fr, this message translates to:
  /// **'Accueil'**
  String get loginBackHome;

  /// No description provided for @otpTitle.
  ///
  /// In fr, this message translates to:
  /// **'Vérification OTP'**
  String get otpTitle;

  /// No description provided for @otpSubtitlePrefix.
  ///
  /// In fr, this message translates to:
  /// **'Code à 6 chiffres envoyé à '**
  String get otpSubtitlePrefix;

  /// No description provided for @otpCodeLabel.
  ///
  /// In fr, this message translates to:
  /// **'Code de vérification'**
  String get otpCodeLabel;

  /// No description provided for @otpVerifyButton.
  ///
  /// In fr, this message translates to:
  /// **'Vérifier le code'**
  String get otpVerifyButton;

  /// No description provided for @otpResendPrompt.
  ///
  /// In fr, this message translates to:
  /// **'Code non reçu ? '**
  String get otpResendPrompt;

  /// No description provided for @otpResendAction.
  ///
  /// In fr, this message translates to:
  /// **'Renvoyer'**
  String get otpResendAction;

  /// No description provided for @otpResendCountdown.
  ///
  /// In fr, this message translates to:
  /// **'Renvoyer dans {seconds}s'**
  String otpResendCountdown(int seconds);

  /// No description provided for @otpBackToLogin.
  ///
  /// In fr, this message translates to:
  /// **'← Retour à la connexion'**
  String get otpBackToLogin;

  /// No description provided for @landingBadge.
  ///
  /// In fr, this message translates to:
  /// **'PLATEFORME DE MICROFINANCE — CAMEROUN'**
  String get landingBadge;

  /// No description provided for @landingTitle.
  ///
  /// In fr, this message translates to:
  /// **'Gérez vos crédits simplement et en sécurité'**
  String get landingTitle;

  /// No description provided for @landingSubtitle.
  ///
  /// In fr, this message translates to:
  /// **'MicroRecouv est une plateforme numérique qui aide les institutions de microfinance à suivre leurs clients, gérer les remboursements et recouvrer les crédits en retard.'**
  String get landingSubtitle;

  /// No description provided for @landingCardTitle.
  ///
  /// In fr, this message translates to:
  /// **'Accès personnel'**
  String get landingCardTitle;

  /// No description provided for @landingCardDescription.
  ///
  /// In fr, this message translates to:
  /// **'Pour les agents, directeurs, caissiers et toute l\'équipe de l\'institution.'**
  String get landingCardDescription;

  /// No description provided for @landingCheckEmailCode.
  ///
  /// In fr, this message translates to:
  /// **'Connexion par code email'**
  String get landingCheckEmailCode;

  /// No description provided for @landingCheckNoPassword.
  ///
  /// In fr, this message translates to:
  /// **'Aucun mot de passe à retenir'**
  String get landingCheckNoPassword;

  /// No description provided for @landingCtaButton.
  ///
  /// In fr, this message translates to:
  /// **'Se connecter'**
  String get landingCtaButton;

  /// No description provided for @dashboardUserFallback.
  ///
  /// In fr, this message translates to:
  /// **'Utilisateur'**
  String get dashboardUserFallback;

  /// No description provided for @dashboardSyncInProgress.
  ///
  /// In fr, this message translates to:
  /// **'Synchronisation en cours…'**
  String get dashboardSyncInProgress;

  /// No description provided for @dashboardSyncPending.
  ///
  /// In fr, this message translates to:
  /// **'{count} collecte(s) en attente de sync'**
  String dashboardSyncPending(int count);

  /// No description provided for @dashboardSyncOk.
  ///
  /// In fr, this message translates to:
  /// **'Synchronisé à l\'instant'**
  String get dashboardSyncOk;

  /// No description provided for @dashboardActivityTitle.
  ///
  /// In fr, this message translates to:
  /// **'Activité du jour'**
  String get dashboardActivityTitle;

  /// No description provided for @dashboardCollectedSuffix.
  ///
  /// In fr, this message translates to:
  /// **'collecté aujourd\'hui'**
  String get dashboardCollectedSuffix;

  /// No description provided for @dashboardStatClientsVisited.
  ///
  /// In fr, this message translates to:
  /// **'Clients visités'**
  String get dashboardStatClientsVisited;

  /// No description provided for @dashboardStatCollectes.
  ///
  /// In fr, this message translates to:
  /// **'Collectes'**
  String get dashboardStatCollectes;

  /// No description provided for @dashboardQuickActionCollecte.
  ///
  /// In fr, this message translates to:
  /// **'Nouvelle\nCollecte'**
  String get dashboardQuickActionCollecte;

  /// No description provided for @dashboardQuickActionClients.
  ///
  /// In fr, this message translates to:
  /// **'Mes\nClients'**
  String get dashboardQuickActionClients;

  /// No description provided for @dashboardAlertesSectionTitle.
  ///
  /// In fr, this message translates to:
  /// **'Alertes sur vos clients'**
  String get dashboardAlertesSectionTitle;

  /// No description provided for @dashboardAlertesError.
  ///
  /// In fr, this message translates to:
  /// **'Impossible de charger les alertes'**
  String get dashboardAlertesError;

  /// No description provided for @dashboardAlertesEmpty.
  ///
  /// In fr, this message translates to:
  /// **'Aucune alerte active'**
  String get dashboardAlertesEmpty;

  /// No description provided for @dashboardAlerteCritique.
  ///
  /// In fr, this message translates to:
  /// **'CRITIQUE'**
  String get dashboardAlerteCritique;

  /// No description provided for @dashboardAlerteActive.
  ///
  /// In fr, this message translates to:
  /// **'ACTIVE'**
  String get dashboardAlerteActive;

  /// No description provided for @profilTitle.
  ///
  /// In fr, this message translates to:
  /// **'Mon Profil'**
  String get profilTitle;

  /// No description provided for @profilUserFallback.
  ///
  /// In fr, this message translates to:
  /// **'Utilisateur'**
  String get profilUserFallback;

  /// No description provided for @profilRoleFallback.
  ///
  /// In fr, this message translates to:
  /// **'Agent Terrain'**
  String get profilRoleFallback;

  /// No description provided for @profilAvatarDelete.
  ///
  /// In fr, this message translates to:
  /// **'Supprimer la photo'**
  String get profilAvatarDelete;

  /// No description provided for @profilAvatarUpdated.
  ///
  /// In fr, this message translates to:
  /// **'Photo de profil mise à jour'**
  String get profilAvatarUpdated;

  /// No description provided for @profilAvatarDeleted.
  ///
  /// In fr, this message translates to:
  /// **'Photo supprimée'**
  String get profilAvatarDeleted;

  /// No description provided for @profilSectionGps.
  ///
  /// In fr, this message translates to:
  /// **'Localisation GPS'**
  String get profilSectionGps;

  /// No description provided for @profilSectionAppearance.
  ///
  /// In fr, this message translates to:
  /// **'Apparence'**
  String get profilSectionAppearance;

  /// No description provided for @profilSectionAccount.
  ///
  /// In fr, this message translates to:
  /// **'Compte'**
  String get profilSectionAccount;

  /// No description provided for @profilSectionSession.
  ///
  /// In fr, this message translates to:
  /// **'Session'**
  String get profilSectionSession;

  /// No description provided for @sessionValidUntil.
  ///
  /// In fr, this message translates to:
  /// **'Valable jusqu\'au {time}'**
  String sessionValidUntil(String time);

  /// No description provided for @sessionExpired.
  ///
  /// In fr, this message translates to:
  /// **'Session de 24 h expirée. Reconnectez-vous.'**
  String get sessionExpired;

  /// No description provided for @sessionDurationHint.
  ///
  /// In fr, this message translates to:
  /// **'24 h à partir de l\'heure de connexion (horodatage terrain).'**
  String get sessionDurationHint;

  /// No description provided for @profilGpsActive.
  ///
  /// In fr, this message translates to:
  /// **'Actif'**
  String get profilGpsActive;

  /// No description provided for @profilGpsOffline.
  ///
  /// In fr, this message translates to:
  /// **'Hors-ligne (positions en file)'**
  String get profilGpsOffline;

  /// No description provided for @profilGpsError.
  ///
  /// In fr, this message translates to:
  /// **'Erreur'**
  String get profilGpsError;

  /// No description provided for @profilGpsIdle.
  ///
  /// In fr, this message translates to:
  /// **'Inactif'**
  String get profilGpsIdle;

  /// No description provided for @profilGpsRequired.
  ///
  /// In fr, this message translates to:
  /// **'Obligatoire'**
  String get profilGpsRequired;

  /// No description provided for @profilGpsCoordinates.
  ///
  /// In fr, this message translates to:
  /// **'Coordonnées'**
  String get profilGpsCoordinates;

  /// No description provided for @profilGpsAccuracy.
  ///
  /// In fr, this message translates to:
  /// **'Précision'**
  String get profilGpsAccuracy;

  /// No description provided for @profilGpsSpeed.
  ///
  /// In fr, this message translates to:
  /// **'Vitesse'**
  String get profilGpsSpeed;

  /// No description provided for @profilGpsPendingPositions.
  ///
  /// In fr, this message translates to:
  /// **'{count} position(s) en attente de synchronisation'**
  String profilGpsPendingPositions(int count);

  /// No description provided for @profilGpsBtnStop.
  ///
  /// In fr, this message translates to:
  /// **'Arrêter le partage GPS'**
  String get profilGpsBtnStop;

  /// No description provided for @profilGpsBtnStopRequired.
  ///
  /// In fr, this message translates to:
  /// **'GPS requis par votre IMF'**
  String get profilGpsBtnStopRequired;

  /// No description provided for @profilGpsBtnStopOffline.
  ///
  /// In fr, this message translates to:
  /// **'Arrêt impossible hors-ligne'**
  String get profilGpsBtnStopOffline;

  /// No description provided for @profilGpsBtnStart.
  ///
  /// In fr, this message translates to:
  /// **'Démarrer le partage GPS'**
  String get profilGpsBtnStart;

  /// No description provided for @profilGpsSnackbarCantStop.
  ///
  /// In fr, this message translates to:
  /// **'Impossible d\'arrêter le GPS : votre IMF exige le partage de position.'**
  String get profilGpsSnackbarCantStop;

  /// No description provided for @profilThemeDarkMode.
  ///
  /// In fr, this message translates to:
  /// **'Mode sombre'**
  String get profilThemeDarkMode;

  /// No description provided for @profilLogoutLabel.
  ///
  /// In fr, this message translates to:
  /// **'Se déconnecter'**
  String get profilLogoutLabel;

  /// No description provided for @profilDialogLogoutTitle.
  ///
  /// In fr, this message translates to:
  /// **'Déconnexion'**
  String get profilDialogLogoutTitle;

  /// No description provided for @profilDialogLogoutContent.
  ///
  /// In fr, this message translates to:
  /// **'Êtes-vous sûr de vouloir vous déconnecter ?'**
  String get profilDialogLogoutContent;

  /// No description provided for @profilDialogConfirmLogout.
  ///
  /// In fr, this message translates to:
  /// **'Déconnecter'**
  String get profilDialogConfirmLogout;

  /// No description provided for @alertesTitle.
  ///
  /// In fr, this message translates to:
  /// **'Alertes'**
  String get alertesTitle;

  /// No description provided for @alertesFilterAll.
  ///
  /// In fr, this message translates to:
  /// **'Toutes'**
  String get alertesFilterAll;

  /// No description provided for @alertesFilterActive.
  ///
  /// In fr, this message translates to:
  /// **'Actives'**
  String get alertesFilterActive;

  /// No description provided for @alertesFilterEscalated.
  ///
  /// In fr, this message translates to:
  /// **'Escaladées'**
  String get alertesFilterEscalated;

  /// No description provided for @alertesFilterTreated.
  ///
  /// In fr, this message translates to:
  /// **'Traitées'**
  String get alertesFilterTreated;

  /// No description provided for @alertesFilterClosed.
  ///
  /// In fr, this message translates to:
  /// **'Clôturées'**
  String get alertesFilterClosed;

  /// No description provided for @alertesLoadError.
  ///
  /// In fr, this message translates to:
  /// **'Impossible de charger les alertes'**
  String get alertesLoadError;

  /// No description provided for @alertesEmpty.
  ///
  /// In fr, this message translates to:
  /// **'Aucune alerte'**
  String get alertesEmpty;

  /// No description provided for @alertesClientUnknown.
  ///
  /// In fr, this message translates to:
  /// **'Client inconnu'**
  String get alertesClientUnknown;

  /// No description provided for @alertesDelayBadge.
  ///
  /// In fr, this message translates to:
  /// **'{days}j retard'**
  String alertesDelayBadge(int days);

  /// No description provided for @alertesAmountDue.
  ///
  /// In fr, this message translates to:
  /// **'{amount} FCFA dû'**
  String alertesAmountDue(String amount);

  /// No description provided for @clientsTitle.
  ///
  /// In fr, this message translates to:
  /// **'Clients'**
  String get clientsTitle;

  /// No description provided for @clientsSearchHint.
  ///
  /// In fr, this message translates to:
  /// **'Rechercher un client...'**
  String get clientsSearchHint;

  /// No description provided for @clientsEmptyTitle.
  ///
  /// In fr, this message translates to:
  /// **'Aucun client trouvé'**
  String get clientsEmptyTitle;

  /// No description provided for @clientsEmptySubtitle.
  ///
  /// In fr, this message translates to:
  /// **'Essayez une autre recherche'**
  String get clientsEmptySubtitle;

  /// No description provided for @clientDetailTitle.
  ///
  /// In fr, this message translates to:
  /// **'Détail client'**
  String get clientDetailTitle;

  /// No description provided for @nouvelleCollecteTitle.
  ///
  /// In fr, this message translates to:
  /// **'Enregistrer une collecte'**
  String get nouvelleCollecteTitle;

  /// No description provided for @nouvelleCollecteSearchHint.
  ///
  /// In fr, this message translates to:
  /// **'Rechercher un client (nom, téléphone…)'**
  String get nouvelleCollecteSearchHint;

  /// No description provided for @nouvelleCollecteChangeClient.
  ///
  /// In fr, this message translates to:
  /// **'Changer'**
  String get nouvelleCollecteChangeClient;

  /// No description provided for @nouvelleCollecteAmountLabel.
  ///
  /// In fr, this message translates to:
  /// **'Montant'**
  String get nouvelleCollecteAmountLabel;

  /// No description provided for @nouvelleCollecteCanalLabel.
  ///
  /// In fr, this message translates to:
  /// **'Canal de paiement'**
  String get nouvelleCollecteCanalLabel;

  /// No description provided for @nouvelleCollecteSubmit.
  ///
  /// In fr, this message translates to:
  /// **'Enregistrer la collecte'**
  String get nouvelleCollecteSubmit;

  /// No description provided for @nouvelleCollecteNoClient.
  ///
  /// In fr, this message translates to:
  /// **'Veuillez sélectionner un client'**
  String get nouvelleCollecteNoClient;

  /// No description provided for @nouvelleCollecteInvalidAmount.
  ///
  /// In fr, this message translates to:
  /// **'Montant invalide'**
  String get nouvelleCollecteInvalidAmount;

  /// No description provided for @confirmationTitle.
  ///
  /// In fr, this message translates to:
  /// **'Collecte enregistrée'**
  String get confirmationTitle;

  /// No description provided for @confirmationSuccessTitle.
  ///
  /// In fr, this message translates to:
  /// **'Collecte sauvegardée !'**
  String get confirmationSuccessTitle;

  /// No description provided for @confirmationSuccessSubtitle.
  ///
  /// In fr, this message translates to:
  /// **'Elle sera synchronisée dès que vous serez connecté.'**
  String get confirmationSuccessSubtitle;

  /// No description provided for @confirmationAmount.
  ///
  /// In fr, this message translates to:
  /// **'Montant'**
  String get confirmationAmount;

  /// No description provided for @confirmationClientId.
  ///
  /// In fr, this message translates to:
  /// **'Client ID'**
  String get confirmationClientId;

  /// No description provided for @confirmationCanal.
  ///
  /// In fr, this message translates to:
  /// **'Canal'**
  String get confirmationCanal;

  /// No description provided for @confirmationDate.
  ///
  /// In fr, this message translates to:
  /// **'Date'**
  String get confirmationDate;

  /// No description provided for @confirmationGps.
  ///
  /// In fr, this message translates to:
  /// **'GPS'**
  String get confirmationGps;

  /// No description provided for @confirmationPendingSync.
  ///
  /// In fr, this message translates to:
  /// **'En attente de synchronisation'**
  String get confirmationPendingSync;

  /// No description provided for @confirmationNewCollecte.
  ///
  /// In fr, this message translates to:
  /// **'Nouvelle collecte'**
  String get confirmationNewCollecte;

  /// No description provided for @historiqueTitle.
  ///
  /// In fr, this message translates to:
  /// **'Historique du jour'**
  String get historiqueTitle;

  /// No description provided for @historiquePendingLabel.
  ///
  /// In fr, this message translates to:
  /// **'En attente'**
  String get historiquePendingLabel;

  /// No description provided for @historiqueTotalAmount.
  ///
  /// In fr, this message translates to:
  /// **'Montant total'**
  String get historiqueTotalAmount;

  /// No description provided for @historiqueSyncing.
  ///
  /// In fr, this message translates to:
  /// **'Synchronisation...'**
  String get historiqueSyncing;

  /// No description provided for @historiqueSyncNow.
  ///
  /// In fr, this message translates to:
  /// **'Synchroniser maintenant'**
  String get historiqueSyncNow;

  /// No description provided for @historiqueLastSync.
  ///
  /// In fr, this message translates to:
  /// **'Dernière sync : {accepted}/{total} — {time}'**
  String historiqueLastSync(int accepted, int total, String time);

  /// No description provided for @historiqueEmptyTitle.
  ///
  /// In fr, this message translates to:
  /// **'Aucune collecte en attente'**
  String get historiqueEmptyTitle;

  /// No description provided for @historiqueEmptySubtitle.
  ///
  /// In fr, this message translates to:
  /// **'Toutes vos collectes sont synchronisées.'**
  String get historiqueEmptySubtitle;

  /// No description provided for @historiqueSyncedTitle.
  ///
  /// In fr, this message translates to:
  /// **'Déjà envoyées au serveur'**
  String get historiqueSyncedTitle;

  /// No description provided for @historiqueListTitle.
  ///
  /// In fr, this message translates to:
  /// **'Collectes ({n})'**
  String historiqueListTitle(int n);

  /// No description provided for @syncResultTitle.
  ///
  /// In fr, this message translates to:
  /// **'Synchronisation terminée'**
  String get syncResultTitle;

  /// No description provided for @syncResultTotal.
  ///
  /// In fr, this message translates to:
  /// **'Total reçu'**
  String get syncResultTotal;

  /// No description provided for @syncResultAccepted.
  ///
  /// In fr, this message translates to:
  /// **'Acceptées'**
  String get syncResultAccepted;

  /// No description provided for @syncResultDuplicates.
  ///
  /// In fr, this message translates to:
  /// **'Doublons'**
  String get syncResultDuplicates;

  /// No description provided for @syncResultRejected.
  ///
  /// In fr, this message translates to:
  /// **'Rejetées'**
  String get syncResultRejected;

  /// No description provided for @syncResultRejectedWarning.
  ///
  /// In fr, this message translates to:
  /// **'{count} collecte(s) non synchronisée(s). Contactez le support.'**
  String syncResultRejectedWarning(int count);

  /// No description provided for @errorTitle.
  ///
  /// In fr, this message translates to:
  /// **'Une erreur est survenue'**
  String get errorTitle;

  /// No description provided for @errorCodeBadge.
  ///
  /// In fr, this message translates to:
  /// **'Erreur {code}'**
  String errorCodeBadge(String code);

  /// No description provided for @errorDefaultMessage.
  ///
  /// In fr, this message translates to:
  /// **'Quelque chose s\'est mal passé. Veuillez réessayer ou revenir à l\'accueil.'**
  String get errorDefaultMessage;

  /// No description provided for @offlineTitle.
  ///
  /// In fr, this message translates to:
  /// **'Connexion indisponible'**
  String get offlineTitle;

  /// No description provided for @offlineDescription.
  ///
  /// In fr, this message translates to:
  /// **'Vérifiez votre connexion Wi-Fi ou données mobiles.\nClients, collectes et tableau de bord restent disponibles depuis le cache local (72 h).'**
  String get offlineDescription;

  /// No description provided for @offlineBanner.
  ///
  /// In fr, this message translates to:
  /// **'Hors ligne — clients et collectes depuis l\'appareil'**
  String get offlineBanner;

  /// No description provided for @offlineCachedChip.
  ///
  /// In fr, this message translates to:
  /// **'Données locales'**
  String get offlineCachedChip;

  /// No description provided for @offlineNoClientsCache.
  ///
  /// In fr, this message translates to:
  /// **'Aucun client en cache. Connectez-vous une fois au réseau pour télécharger le portefeuille.'**
  String get offlineNoClientsCache;

  /// No description provided for @serverSection.
  ///
  /// In fr, this message translates to:
  /// **'Serveur'**
  String get serverSection;

  /// No description provided for @serverProduction.
  ///
  /// In fr, this message translates to:
  /// **'En ligne (prod)'**
  String get serverProduction;

  /// No description provided for @serverStaging.
  ///
  /// In fr, this message translates to:
  /// **'Staging'**
  String get serverStaging;

  /// No description provided for @serverLocal.
  ///
  /// In fr, this message translates to:
  /// **'Serveur local'**
  String get serverLocal;

  /// No description provided for @serverSwitchTitle.
  ///
  /// In fr, this message translates to:
  /// **'Changer de serveur ?'**
  String get serverSwitchTitle;

  /// No description provided for @serverSwitchContent.
  ///
  /// In fr, this message translates to:
  /// **'Les collectes en attente restent sur l\'appareil et seront envoyées au nouveau serveur après reconnexion. Le cache clients sera retéléchargé.'**
  String get serverSwitchContent;

  /// No description provided for @serverSwitchPending.
  ///
  /// In fr, this message translates to:
  /// **'{count} collecte(s) encore locales seront transférées.'**
  String serverSwitchPending(int count);

  /// No description provided for @serverSwitchConfirm.
  ///
  /// In fr, this message translates to:
  /// **'Changer et se reconnecter'**
  String get serverSwitchConfirm;

  /// No description provided for @serverCurrent.
  ///
  /// In fr, this message translates to:
  /// **'Actuel : {url}'**
  String serverCurrent(String url);

  /// No description provided for @offlineBadge.
  ///
  /// In fr, this message translates to:
  /// **'Vos collectes sont sauvegardées localement'**
  String get offlineBadge;

  /// No description provided for @offlineChecking.
  ///
  /// In fr, this message translates to:
  /// **'Vérification...'**
  String get offlineChecking;

  /// No description provided for @offlineCheckButton.
  ///
  /// In fr, this message translates to:
  /// **'Vérifier la connexion'**
  String get offlineCheckButton;

  /// No description provided for @offlineStillOffline.
  ///
  /// In fr, this message translates to:
  /// **'Toujours pas de connexion'**
  String get offlineStillOffline;

  /// No description provided for @langFr.
  ///
  /// In fr, this message translates to:
  /// **'Français'**
  String get langFr;

  /// No description provided for @langEn.
  ///
  /// In fr, this message translates to:
  /// **'English'**
  String get langEn;

  /// No description provided for @langSwitchTooltip.
  ///
  /// In fr, this message translates to:
  /// **'Changer la langue'**
  String get langSwitchTooltip;

  /// No description provided for @navHome.
  ///
  /// In fr, this message translates to:
  /// **'Accueil'**
  String get navHome;

  /// No description provided for @navClients.
  ///
  /// In fr, this message translates to:
  /// **'Clients'**
  String get navClients;

  /// No description provided for @navHistorique.
  ///
  /// In fr, this message translates to:
  /// **'Historique'**
  String get navHistorique;

  /// No description provided for @errorWithDetail.
  ///
  /// In fr, this message translates to:
  /// **'Erreur : {detail}'**
  String errorWithDetail(String detail);
}

class _AppL10nDelegate extends LocalizationsDelegate<AppL10n> {
  const _AppL10nDelegate();

  @override
  Future<AppL10n> load(Locale locale) {
    return SynchronousFuture<AppL10n>(lookupAppL10n(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'fr'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppL10nDelegate old) => false;
}

AppL10n lookupAppL10n(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppL10nEn();
    case 'fr':
      return AppL10nFr();
  }

  throw FlutterError(
      'AppL10n.delegate failed to load unsupported locale "$locale". This is likely '
      'an issue with the localizations generation tool. Please file an issue '
      'on GitHub with a reproducible sample app and the gen-l10n configuration '
      'that was used.');
}
