import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../core/providers/auth_provider.dart';
import '../screens/splash/splash_screen.dart';
import '../screens/landing/landing_screen.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/otp_screen.dart';
import '../screens/dashboard/dashboard_screen.dart';
import '../screens/clients/clients_list_screen.dart';
import '../screens/clients/client_detail_screen.dart';
import '../screens/collectes/nouvelle_collecte_screen.dart';
import '../screens/collectes/confirmation_collecte_screen.dart';
import '../screens/collectes/historique_jour_screen.dart';
import '../screens/collectes/sync_result_screen.dart';
import '../screens/profil/profil_screen.dart';
import '../screens/alertes/alertes_screen.dart';
import '../screens/error/error_screen.dart';
import '../screens/error/offline_screen.dart';
import '../core/models/collecte_locale.dart';

class AppRouter {
  static final GoRouter router = GoRouter(
    initialLocation: '/',
    debugLogDiagnostics: true,
    redirect: (context, state) {
      final auth = context.read<AuthProvider>();
      final location = state.matchedLocation;
      final publicRoutes = ['/', '/landing', '/login', '/otp'];
      if (!auth.isAuthenticated && !publicRoutes.contains(location)) return '/login';
      if (auth.isAuthenticated &&
          auth.currentUser?.role != 'AGENT' &&
          !publicRoutes.contains(location)) {
        return '/login';
      }
      if (auth.isAuthenticated && (location == '/login' || location == '/otp' || location == '/landing')) return '/dashboard';
      return null;
    },
    routes: [
      GoRoute(path: '/', builder: (_, __) => const SplashScreen()),
      GoRoute(path: '/landing', builder: (_, __) => const LandingScreen()),
      GoRoute(path: '/login', builder: (_, __) => const LoginScreen()),
      GoRoute(path: '/otp', builder: (_, __) => const OtpScreen()),
      GoRoute(path: '/dashboard', builder: (_, __) => const DashboardScreen()),

      // Clients
      GoRoute(path: '/clients', builder: (_, __) => const ClientsListScreen()),
      GoRoute(
        path: '/clients/:id',
        builder: (context, state) => ClientDetailScreen(idClient: state.pathParameters['id'] ?? ''),
      ),

      // Collectes
      GoRoute(path: '/collectes/nouvelle', builder: (_, __) => const NouvelleCollecteScreen()),
      GoRoute(
        path: '/collectes/confirmation',
        builder: (context, state) => ConfirmationCollecteScreen(collecte: state.extra as CollecteLocale),
      ),
      GoRoute(path: '/collectes/historique', builder: (_, __) => const HistoriqueJourScreen()),
      GoRoute(path: '/historique', redirect: (_, __) => '/collectes/historique'),

      // Profil & Alertes
      GoRoute(path: '/profil', builder: (_, __) => const ProfilScreen()),
      GoRoute(path: '/alertes', builder: (_, __) => const AlertesScreen()),
      GoRoute(
        path: '/collectes/sync-result',
        builder: (context, state) {
          final result = state.extra as SyncResult?;
          return SyncResultScreen(
            result: result ?? SyncResult(totalRecu: 0, acceptees: 0, doublons: 0, rejetees: 0, syncedAt: DateTime.now()),
          );
        },
      ),

      // Error / Offline
      GoRoute(
        path: '/error',
        builder: (context, state) => ErrorScreen(
          message: state.uri.queryParameters['message'],
          code: state.uri.queryParameters['code'],
        ),
      ),
      GoRoute(
        path: '/offline',
        builder: (context, state) => OfflineScreen(onConnected: () => context.go('/dashboard')),
      ),
    ],
    errorBuilder: (context, state) => ErrorScreen(
      message: 'Page introuvable : ${state.uri}',
      code: '404',
      onRetry: () => context.go('/dashboard'),
    ),
  );
}
