import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../core/providers/auth_provider.dart';
import '../screens/splash/splash_screen.dart';
import '../screens/landing/landing_screen.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/otp_screen.dart';
import '../screens/dashboard/dashboard_screen.dart';
import '../screens/prets/prets_list_screen.dart';
import '../screens/prets/pret_detail_screen.dart';
import '../screens/alertes/alertes_list_screen.dart';
import '../screens/alertes/alerte_detail_screen.dart';
import '../screens/clients/clients_list_screen.dart';
import '../screens/clients/client_detail_screen.dart';
import '../screens/profil/profil_screen.dart';
import '../screens/collectes/nouvelle_collecte_screen.dart';
import '../screens/collectes/confirmation_collecte_screen.dart';
import '../screens/collectes/historique_jour_screen.dart';
import '../screens/collectes/sync_result_screen.dart';
import '../screens/support/ticket_support_screen.dart';
import '../screens/credit/dossiers_credit_screen.dart';
import '../screens/delegations/mes_delegations_screen.dart';
import '../screens/recouvrement/recouvrement_list_screen.dart';
import '../screens/caisse/caisse_screen.dart';
import '../screens/back_office/back_office_screen.dart';
import '../core/models/collecte_locale.dart'; // CollecteLocale + SyncResult

class AppRouter {
  static final GoRouter router = GoRouter(
    initialLocation: '/',
    debugLogDiagnostics: true,
    redirect: (context, state) {
      final authProvider = context.read<AuthProvider>();
      final isAuthenticated = authProvider.isAuthenticated;
      final location = state.matchedLocation;

      final publicRoutes = ['/', '/landing', '/login', '/otp'];
      final isPublic = publicRoutes.contains(location);

      if (!isAuthenticated && !isPublic) {
        return '/login';
      }
      if (isAuthenticated && (location == '/login' || location == '/otp' || location == '/landing')) {
        return '/dashboard';
      }
      return null;
    },
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: '/landing',
        builder: (context, state) => const LandingScreen(),
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/otp',
        builder: (context, state) => const OtpScreen(),
      ),
      GoRoute(
        path: '/dashboard',
        builder: (context, state) => const DashboardScreen(),
      ),

      // ── Prêts ────────────────────────────────────────────────────────────────
      GoRoute(
        path: '/prets',
        builder: (context, state) => const PretsListScreen(),
      ),
      GoRoute(
        path: '/prets/:id',
        builder: (context, state) {
          final id = int.tryParse(state.pathParameters['id'] ?? '0') ?? 0;
          return PretDetailScreen(idPret: id);
        },
      ),

      // ── Alertes ──────────────────────────────────────────────────────────────
      GoRoute(
        path: '/alertes',
        builder: (context, state) => const AlertesListScreen(),
      ),
      GoRoute(
        path: '/alertes/:id',
        builder: (context, state) {
          final id = int.tryParse(state.pathParameters['id'] ?? '0') ?? 0;
          return AlerteDetailScreen(alerteId: id);
        },
      ),

      // ── Clients ──────────────────────────────────────────────────────────────
      GoRoute(
        path: '/clients',
        builder: (context, state) => const ClientsListScreen(),
      ),
      GoRoute(
        path: '/clients/:id',
        builder: (context, state) {
          final id = state.pathParameters['id'] ?? '';
          return ClientDetailScreen(idClient: id);
        },
      ),

      // ── Profil ───────────────────────────────────────────────────────────────
      GoRoute(
        path: '/profil',
        builder: (context, state) => const ProfilScreen(),
      ),

      // ── Collectes ────────────────────────────────────────────────────────────
      GoRoute(
        path: '/collectes/nouvelle',
        builder: (context, state) => const NouvelleCollecteScreen(),
      ),
      GoRoute(
        path: '/collectes/confirmation',
        builder: (context, state) {
          final collecte = state.extra as CollecteLocale;
          return ConfirmationCollecteScreen(collecte: collecte);
        },
      ),
      GoRoute(
        path: '/collectes/historique',
        builder: (context, state) => const HistoriqueJourScreen(),
      ),
      GoRoute(
        path: '/collectes/sync-result',
        builder: (context, state) {
          final result = state.extra as SyncResult?;
          return SyncResultScreen(
            result: result ?? SyncResult(totalRecu: 0, acceptees: 0, doublons: 0, rejetees: 0, syncedAt: DateTime.now()),
          );
        },
      ),

      // ── Support ──────────────────────────────────────────────────────────────
      GoRoute(
        path: '/support/ticket',
        builder: (context, state) => const TicketSupportScreen(),
      ),

      // ── Crédit (dossiers + réassignation) ────────────────────────────────────
      GoRoute(
        path: '/credit',
        builder: (context, state) => const DossiersCreditScreen(),
      ),

      // ── Délégations hiérarchiques ─────────────────────────────────────────────
      GoRoute(
        path: '/delegations',
        builder: (context, state) => const MesDelegationsScreen(),
      ),

      // ── Recouvrement (RESPONSABLE_RECOUVREMENT, DIRECTEUR) ───────────────────
      GoRoute(
        path: '/recouvrement',
        builder: (context, state) => const RecouvrementListScreen(),
      ),

      // ── Caisse (CAISSIER, CHEF_AGENCE) ───────────────────────────────────────
      GoRoute(
        path: '/caisse',
        builder: (context, state) => const CaisseScreen(),
      ),

      // ── Back-Office (AGENT_SAISIE) ───────────────────────────────────────────
      GoRoute(
        path: '/back-office',
        builder: (context, state) => const BackOfficeScreen(),
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 64, color: Colors.red),
            const SizedBox(height: 16),
            Text('Page introuvable: ${state.uri}'),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () => context.go('/dashboard'),
              child: const Text('Retour au tableau de bord'),
            ),
          ],
        ),
      ),
    ),
  );
}
