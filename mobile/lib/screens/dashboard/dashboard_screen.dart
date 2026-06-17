import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/models/alerte.dart';
import '../../core/models/kpi_summary.dart';
import '../../core/providers/auth_provider.dart';
import '../../core/providers/sync_provider.dart';
import '../../core/services/alerte_service.dart';
import '../../core/services/kpi_service.dart';
import '../../widgets/app_bottom_nav.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  KpiSummary? _kpi;
  List<Alerte> _alertes = [];
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final kpiService = context.read<KpiService>();
      final alerteService = context.read<AlerteService>();
      final now = DateTime.now();
      final dateDebut = DateFormat('yyyy-MM-dd').format(DateTime(now.year, now.month, 1));
      final dateFin = DateFormat('yyyy-MM-dd').format(now);

      final results = await Future.wait([
        kpiService.getSummary(dateDebut: dateDebut, dateFin: dateFin),
        alerteService.getAlertes(statut: 'ACTIVE', page: 0, size: 5),
      ]);

      setState(() {
        _kpi = results[0] as KpiSummary;
        _alertes = (results[1] as dynamic).content as List<Alerte>;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthProvider>();
    final sync = context.watch<SyncProvider>();
    final user = auth.currentUser;
    final name = user?.fullName ?? user?.username ?? 'Utilisateur';
    final initials = name
        .trim()
        .split(' ')
        .where((s) => s.isNotEmpty)
        .take(2)
        .map((s) => s[0].toUpperCase())
        .join();
    final role = user?.displayRole ?? user?.role ?? '';

    return Scaffold(
      backgroundColor: AppColors.darkBg,
      body: Column(
        children: [
          _buildTopBar(context, sync, initials, name, role),
          Expanded(
            child: RefreshIndicator(
              onRefresh: _loadData,
              color: AppColors.gold,
              backgroundColor: AppColors.darkSurface,
              child: ListView(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                children: [
                  _buildSyncBanner(sync),
                  const SizedBox(height: 16),
                  _buildObjectifCard(_kpi),
                  const SizedBox(height: 16),
                  _buildQuickActions(),
                  const SizedBox(height: 24),
                  _buildAlertesSection(),
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        ],
      ),
      bottomNavigationBar: const AppBottomNav(currentIndex: 0),
    );
  }

  Widget _buildTopBar(
    BuildContext context,
    SyncProvider sync,
    String initials,
    String name,
    String role,
  ) {
    return Container(
      color: AppColors.navyDark,
      padding: EdgeInsets.only(
        top: MediaQuery.of(context).padding.top + 10,
        bottom: 14,
        left: 16,
        right: 16,
      ),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: const BoxDecoration(
              color: AppColors.navy,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                initials.isNotEmpty ? initials : 'U',
                style: const TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 16,
                  fontWeight: FontWeight.w800,
                  color: Colors.white,
                ),
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 15,
                    fontWeight: FontWeight.w700,
                    color: Colors.white,
                  ),
                ),
                Text(
                  role,
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 11,
                    color: AppColors.textSecondary,
                  ),
                ),
              ],
            ),
          ),
          GestureDetector(
            onTap: sync.syncing ? null : () => sync.syncNow(),
            child: sync.syncing
                ? const SizedBox(
                    width: 22,
                    height: 22,
                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                  )
                : const Icon(Icons.sync_rounded, color: Colors.white, size: 22),
          ),
          const SizedBox(width: 18),
          GestureDetector(
            onTap: () => context.go('/alertes'),
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                const Icon(Icons.notifications_outlined, color: Colors.white, size: 22),
                if ((_kpi?.nbAlertesActives ?? 0) > 0)
                  Positioned(
                    right: -2,
                    top: -2,
                    child: Container(
                      width: 8,
                      height: 8,
                      decoration: const BoxDecoration(
                        color: AppColors.error,
                        shape: BoxShape.circle,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSyncBanner(SyncProvider sync) {
    Color color;
    IconData icon;
    String label;

    if (sync.syncing || sync.scoringState == ScoringState.pending) {
      color = AppColors.warning;
      icon = Icons.autorenew_rounded;
      label = 'Synchronisation en cours…';
    } else if (sync.pendingCount > 0) {
      color = AppColors.warning;
      icon = Icons.cloud_upload_outlined;
      label = '${sync.pendingCount} collecte(s) en attente de sync';
    } else {
      color = AppColors.success;
      icon = Icons.check_circle_outline_rounded;
      label = 'Synchronisé à l\'instant';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withOpacity(0.35)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 16),
          const SizedBox(width: 8),
          Text(
            label,
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: color,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildObjectifCard(KpiSummary? kpi) {
    const double targetAmount = 350000;
    final collected = kpi?.totalCollectes ?? 0.0;
    final progress = (collected / targetAmount).clamp(0.0, 1.0);
    final collectes = kpi?.nbCollectes ?? 0;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.darkSurface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.darkBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Objectif du jour',
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  color: AppColors.textSecondary,
                ),
              ),
              Text(
                '${NumberFormat('#,###', 'fr_FR').format(targetAmount.toInt())} FCFA',
                style: const TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: AppColors.gold,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 8,
              backgroundColor: AppColors.darkBorder,
              valueColor: const AlwaysStoppedAnimation(AppColors.gold),
            ),
          ),
          const SizedBox(height: 20),
          Row(
            children: [
              _buildStat(Icons.people_outline_rounded, 'Clients visités', '$collectes/20'),
              const SizedBox(width: 28),
              _buildStat(Icons.receipt_long_rounded, 'Collectes', '$collectes'),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildStat(IconData icon, String label, String value) {
    return Row(
      children: [
        Icon(icon, color: AppColors.textSecondary, size: 16),
        const SizedBox(width: 8),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              style: const TextStyle(
                fontFamily: 'Inter',
                fontSize: 10,
                color: AppColors.textSecondary,
              ),
            ),
            Text(
              value,
              style: const TextStyle(
                fontFamily: 'Inter',
                fontSize: 15,
                fontWeight: FontWeight.w700,
                color: Colors.white,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildQuickActions() {
    return Row(
      children: [
        Expanded(
          child: _buildActionTile(
            icon: Icons.account_balance_wallet_rounded,
            label: 'Nouvelle\nCollecte',
            onTap: () => context.go('/collectes/nouvelle'),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _buildActionTile(
            icon: Icons.people_rounded,
            label: 'Mes\nClients',
            onTap: () => context.go('/clients'),
          ),
        ),
      ],
    );
  }

  Widget _buildActionTile({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 22),
        decoration: BoxDecoration(
          color: AppColors.darkSurface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.darkBorder),
        ),
        child: Column(
          children: [
            Icon(icon, color: AppColors.gold, size: 30),
            const SizedBox(height: 10),
            Text(
              label,
              textAlign: TextAlign.center,
              style: const TextStyle(
                fontFamily: 'Inter',
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: Colors.white,
                height: 1.3,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAlertesSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text(
              'Alertes sur vos clients',
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: Colors.white,
              ),
            ),
            GestureDetector(
              onTap: () => context.go('/alertes'),
              child: const Text(
                'Voir tout',
                style: TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: AppColors.teal,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (_loading)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 24),
            child: Center(child: CircularProgressIndicator(color: AppColors.gold)),
          )
        else if (_error != null)
          _buildEmptyCard('Impossible de charger les alertes')
        else if (_alertes.isEmpty)
          _buildEmptyCard('Aucune alerte active')
        else
          ..._alertes.map(
            (a) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _buildAlerteItem(a),
            ),
          ),
      ],
    );
  }

  Widget _buildEmptyCard(String message) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.darkSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.darkBorder),
      ),
      child: Center(
        child: Text(
          message,
          style: const TextStyle(
            fontFamily: 'Inter',
            fontSize: 13,
            color: AppColors.textSecondary,
          ),
        ),
      ),
    );
  }

  Widget _buildAlerteItem(Alerte a) {
    final isCritique = a.isEscaladee;
    return GestureDetector(
      onTap: () => context.go('/alertes/${a.id}'),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.darkSurface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.darkBorder),
        ),
        child: Row(
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(
                color: AppColors.error.withOpacity(0.14),
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.warning_amber_rounded, color: AppColors.error, size: 18),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    a.nomClient ?? 'Prêt #${a.idPret}',
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    a.message ?? 'Alerte active',
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 12,
                      color: AppColors.textSecondary,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: AppColors.error.withOpacity(0.14),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                isCritique ? 'CRITIQUE' : 'ACTIVE',
                style: const TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 10,
                  fontWeight: FontWeight.w700,
                  color: AppColors.error,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
