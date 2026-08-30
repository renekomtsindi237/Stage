// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppL10nEn extends AppL10n {
  AppL10nEn([String locale = 'en']) : super(locale);

  @override
  String get appName => 'MicroRecouv';

  @override
  String get appTagline => 'Intelligent Management & Recovery';

  @override
  String get appLoading => 'Loading...';

  @override
  String get appVersion => 'v1.0.0';

  @override
  String get appImfCameroun => 'IMF Cameroon';

  @override
  String get cancel => 'Cancel';

  @override
  String get retry => 'Retry';

  @override
  String get backHome => 'Back to home';

  @override
  String get error => 'Error';

  @override
  String get confirm => 'Confirm';

  @override
  String get close => 'Close';

  @override
  String get save => 'Save';

  @override
  String get send => 'Send';

  @override
  String get refresh => 'Refresh';

  @override
  String get currencyFcfa => 'FCFA';

  @override
  String get currencyF => 'F';

  @override
  String get loginTitle => 'Sign In';

  @override
  String get loginSubtitle =>
      'A verification code will be sent to your email address';

  @override
  String get loginEmailLabel => 'Professional email address';

  @override
  String get loginEmailHint => 'your@institution.cm or example@gmail.com';

  @override
  String get loginEmailRequired => 'Email required';

  @override
  String get loginEmailInvalid => 'Invalid email';

  @override
  String get loginSubmitButton => 'Receive my code';

  @override
  String get loginFooterHelp => 'Connection problem? Contact the administrator';

  @override
  String get loginNetworkError => 'Network error';

  @override
  String get loginBackHome => 'Home';

  @override
  String get otpTitle => 'OTP Verification';

  @override
  String get otpSubtitlePrefix => '6-digit code sent to ';

  @override
  String get otpCodeLabel => 'Verification code';

  @override
  String get otpVerifyButton => 'Verify code';

  @override
  String get otpResendPrompt => 'Didn\'t receive the code? ';

  @override
  String get otpResendAction => 'Resend';

  @override
  String otpResendCountdown(int seconds) {
    return 'Resend in ${seconds}s';
  }

  @override
  String get otpBackToLogin => '← Back to sign in';

  @override
  String get landingBadge => 'MICROFINANCE PLATFORM — CAMEROON';

  @override
  String get landingTitle => 'Manage your credits simply and securely';

  @override
  String get landingSubtitle =>
      'MicroRecouv is a digital platform that helps microfinance institutions track their clients, manage repayments and recover overdue credits.';

  @override
  String get landingCardTitle => 'Personal access';

  @override
  String get landingCardDescription =>
      'For agents, directors, cashiers and the entire institution team.';

  @override
  String get landingCheckEmailCode => 'Sign in with email code';

  @override
  String get landingCheckNoPassword => 'No password to remember';

  @override
  String get landingCtaButton => 'Sign in';

  @override
  String get dashboardUserFallback => 'User';

  @override
  String get dashboardSyncInProgress => 'Synchronization in progress…';

  @override
  String dashboardSyncPending(int count) {
    return '$count collection(s) pending sync';
  }

  @override
  String get dashboardSyncOk => 'Just synchronized';

  @override
  String get dashboardActivityTitle => 'Today\'s activity';

  @override
  String get dashboardCollectedSuffix => 'collected today';

  @override
  String get dashboardStatClientsVisited => 'Clients visited';

  @override
  String get dashboardStatCollectes => 'Collections';

  @override
  String get dashboardQuickActionCollecte => 'New\nCollection';

  @override
  String get dashboardQuickActionClients => 'My\nClients';

  @override
  String get dashboardAlertesSectionTitle => 'Alerts on your clients';

  @override
  String get dashboardAlertesError => 'Unable to load alerts';

  @override
  String get dashboardAlertesEmpty => 'No active alert';

  @override
  String get dashboardAlerteCritique => 'CRITICAL';

  @override
  String get dashboardAlerteActive => 'ACTIVE';

  @override
  String get profilTitle => 'My Profile';

  @override
  String get profilUserFallback => 'User';

  @override
  String get profilRoleFallback => 'Field Agent';

  @override
  String get profilAvatarDelete => 'Delete photo';

  @override
  String get profilAvatarUpdated => 'Profile photo updated';

  @override
  String get profilAvatarDeleted => 'Photo deleted';

  @override
  String get profilSectionGps => 'GPS Location';

  @override
  String get profilSectionAppearance => 'Appearance';

  @override
  String get profilSectionAccount => 'Account';

  @override
  String get profilSectionSession => 'Session';

  @override
  String sessionValidUntil(String time) {
    return 'Valid until $time';
  }

  @override
  String get sessionExpired => '24-hour session expired. Please sign in again.';

  @override
  String get sessionDurationHint => '24 hours from the login timestamp.';

  @override
  String get profilGpsActive => 'Active';

  @override
  String get profilGpsOffline => 'Offline (positions queued)';

  @override
  String get profilGpsError => 'Error';

  @override
  String get profilGpsIdle => 'Inactive';

  @override
  String get profilGpsRequired => 'Required';

  @override
  String get profilGpsCoordinates => 'Coordinates';

  @override
  String get profilGpsAccuracy => 'Accuracy';

  @override
  String get profilGpsSpeed => 'Speed';

  @override
  String profilGpsPendingPositions(int count) {
    return '$count position(s) pending synchronization';
  }

  @override
  String get profilGpsBtnStop => 'Stop GPS sharing';

  @override
  String get profilGpsBtnStopRequired => 'GPS required by your IMF';

  @override
  String get profilGpsBtnStopOffline => 'Cannot stop while offline';

  @override
  String get profilGpsBtnStart => 'Start GPS sharing';

  @override
  String get profilGpsSnackbarCantStop =>
      'Cannot stop GPS: your IMF requires location sharing.';

  @override
  String get profilThemeDarkMode => 'Dark mode';

  @override
  String get profilLogoutLabel => 'Sign out';

  @override
  String get profilDialogLogoutTitle => 'Sign out';

  @override
  String get profilDialogLogoutContent => 'Are you sure you want to sign out?';

  @override
  String get profilDialogConfirmLogout => 'Sign out';

  @override
  String get alertesTitle => 'Alerts';

  @override
  String get alertesFilterAll => 'All';

  @override
  String get alertesFilterActive => 'Active';

  @override
  String get alertesFilterEscalated => 'Escalated';

  @override
  String get alertesFilterTreated => 'Treated';

  @override
  String get alertesFilterClosed => 'Closed';

  @override
  String get alertesLoadError => 'Unable to load alerts';

  @override
  String get alertesEmpty => 'No alerts';

  @override
  String get alertesClientUnknown => 'Unknown client';

  @override
  String alertesDelayBadge(int days) {
    return '${days}d overdue';
  }

  @override
  String alertesAmountDue(String amount) {
    return '$amount FCFA due';
  }

  @override
  String get clientsTitle => 'Clients';

  @override
  String get clientsSearchHint => 'Search for a client...';

  @override
  String get clientsEmptyTitle => 'No client found';

  @override
  String get clientsEmptySubtitle => 'Try a different search';

  @override
  String get clientDetailTitle => 'Client detail';

  @override
  String get nouvelleCollecteTitle => 'Record a collection';

  @override
  String get nouvelleCollecteSearchHint => 'Search for a client (name, phone…)';

  @override
  String get nouvelleCollecteChangeClient => 'Change';

  @override
  String get nouvelleCollecteAmountLabel => 'Amount';

  @override
  String get nouvelleCollecteCanalLabel => 'Payment channel';

  @override
  String get nouvelleCollecteSubmit => 'Record the collection';

  @override
  String get nouvelleCollecteNoClient => 'Please select a client';

  @override
  String get nouvelleCollecteInvalidAmount => 'Invalid amount';

  @override
  String get confirmationTitle => 'Collection recorded';

  @override
  String get confirmationSuccessTitle => 'Collection saved!';

  @override
  String get confirmationSuccessSubtitle =>
      'It will be synchronized as soon as you are connected.';

  @override
  String get confirmationAmount => 'Amount';

  @override
  String get confirmationClientId => 'Client ID';

  @override
  String get confirmationCanal => 'Channel';

  @override
  String get confirmationDate => 'Date';

  @override
  String get confirmationGps => 'GPS';

  @override
  String get confirmationPendingSync => 'Pending synchronization';

  @override
  String get confirmationNewCollecte => 'New collection';

  @override
  String get historiqueTitle => 'Today\'s history';

  @override
  String get historiquePendingLabel => 'Pending';

  @override
  String get historiqueTotalAmount => 'Total amount';

  @override
  String get historiqueSyncing => 'Synchronizing...';

  @override
  String get historiqueSyncNow => 'Synchronize now';

  @override
  String historiqueLastSync(int accepted, int total, String time) {
    return 'Last sync: $accepted/$total — $time';
  }

  @override
  String get historiqueEmptyTitle => 'No pending collection';

  @override
  String get historiqueEmptySubtitle =>
      'All your collections are synchronized.';

  @override
  String get historiqueSyncedTitle => 'Already sent to the server';

  @override
  String historiqueListTitle(int n) {
    return 'Collections ($n)';
  }

  @override
  String get syncResultTitle => 'Synchronization complete';

  @override
  String get syncResultTotal => 'Total received';

  @override
  String get syncResultAccepted => 'Accepted';

  @override
  String get syncResultDuplicates => 'Duplicates';

  @override
  String get syncResultRejected => 'Rejected';

  @override
  String syncResultRejectedWarning(int count) {
    return '$count collection(s) not synchronized. Contact support.';
  }

  @override
  String get errorTitle => 'An error occurred';

  @override
  String errorCodeBadge(String code) {
    return 'Error $code';
  }

  @override
  String get errorDefaultMessage =>
      'Something went wrong. Please try again or go back to home.';

  @override
  String get offlineTitle => 'Connection unavailable';

  @override
  String get offlineDescription =>
      'Check your Wi-Fi or mobile data.\nClients, collections and the dashboard stay available from the local cache (72 h).';

  @override
  String get offlineBanner =>
      'Offline — clients and collections from this device';

  @override
  String get offlineCachedChip => 'Local data';

  @override
  String get offlineNoClientsCache =>
      'No clients cached. Connect once to download your portfolio.';

  @override
  String get serverSection => 'Server';

  @override
  String get serverProduction => 'Online (prod)';

  @override
  String get serverStaging => 'Staging';

  @override
  String get serverLocal => 'Local server';

  @override
  String get serverSwitchTitle => 'Switch server?';

  @override
  String get serverSwitchContent =>
      'Pending collections stay on this device and will be sent to the new server after you sign in again. The client cache will be downloaded again.';

  @override
  String serverSwitchPending(int count) {
    return '$count local collection(s) will be transferred.';
  }

  @override
  String get serverSwitchConfirm => 'Switch and sign in again';

  @override
  String serverCurrent(String url) {
    return 'Current: $url';
  }

  @override
  String get offlineBadge => 'Your collections are saved locally';

  @override
  String get offlineChecking => 'Checking...';

  @override
  String get offlineCheckButton => 'Check connection';

  @override
  String get offlineStillOffline => 'Still no connection';

  @override
  String get langFr => 'Français';

  @override
  String get langEn => 'English';

  @override
  String get langSwitchTooltip => 'Change language';

  @override
  String get navHome => 'Home';

  @override
  String get navClients => 'Clients';

  @override
  String get navHistorique => 'History';

  @override
  String errorWithDetail(String detail) {
    return 'Error: $detail';
  }
}
