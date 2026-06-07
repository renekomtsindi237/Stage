import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/models/alerte.dart';
import '../../core/models/kpi_summary.dart';
import '../../core/models/pret.dart';
import '../../core/providers/auth_provider.dart';
import '../../core/services/alerte_service.dart';
import '../../core/services/kpi_service.dart';
import '../../core/services/pret_service.dart';
import '../../widgets/app_bottom_nav.dart';
import '../../widgets/error_widget.dart';
import '../../widgets/kpi_card.dart';
import '../../widgets/quick_access_tile.dart';
import '../../widgets/skeleton_loader.dart';
import '../../widgets/status_badge.dart';
import '../../widgets/transaction_item.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  KpiSummary? _kpi;
  List<Alerte> _recentAlertes = [];
  List<Pret> _recentPrets = [];
  bool _loading = true;
  String? _error;
  late final KpiService _kpiService;
  late final AlerteService _alerteService;
  late final PretService _pretService;

  @override
  void initState() {
    super.initState();
    final context2 = context;
    _kpiService = context2.read<KpiService>();
    _alerteService = context2.read<AlerteService>();
    _pretService = context2.read<PretService>();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final now = DateTime.now();
      final firstOfMonth = DateTime(now.year, now.month, 1);
      final dateDebut = DateFormat('yyyy-MM-dd').format(firstOfMonth);
      final dateFin = DateFormat('yyyy-MM-dd').format(now);

      final results = await Future.wait([
        _kpiService.getSummary(dateDebut: dateDebut, dateFin: dateFin),
        _alerteService.getAlertes(statut: 'ACTIVE', page: 0, size: 5),
        _pretService.getPrets(statut: 'EN_RETARD', page: 0, size: 5),
      ]);

      setState(() {
        _kpi = results[0] as KpiSummary;
        _recentAlertes = (results[1] as dynamic).content as List<Alerte>;
        _recentPrets = (results[2] as dynamic).content as List<Pret>;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  String _formatCurrency(double value) {
    return NumberFormat.currency(
      locale: 'fr_CM',
      symbol: 'FCFA',
      decimalDigits: 0,
    ).format(value);
  }

  @override
  Widget build(BuildContext context) {
    final authProvider = context.watch<AuthProvider>();
    final user = authProvider.currentUser;

    return Scaffold(
      backgroundColor: AppColors.darkBg,
      body: RefreshIndicator(
        onRefresh: _loadData,
        color: AppColors.gold,
        backgroundColor: AppColors.darkSurface,
        child: CustomScrollView(
          slivers: [
            SliverAppBar(
              expandedHeight: 0,
              floating: true,
              backgroundColor: AppColors.darkBg,
              actions: [
                IconButton(
                  onPressed: () => context.go('/profil'),
                  icon: const Icon(Icons.person_outline_rounded, color: AppColors.textSecondary),
                ),
                const SizedBox(width: 8),
              ],
            ),
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 20),
              sliver: SliverList(
                delegate: SliverChildListDelegate([
                  if (_loading) ...[
                    const SkeletonDashboardHeader(),
                    const SizedBox(height: 20),
                    const SkeletonCard(),
                    const SizedBox(height: 12),
                    const SkeletonCard(),
                    const SizedBox(height: 20),
                    Row(children: const [
                      Expanded(child: SkeletonCard()),
                      SizedBox(width: 12),
                      Expanded(child: SkeletonCard()),
                      SizedBox(width: 12),
                      Expanded(child: SkeletonCard()),
                    ]),
                  ] else if (_error != null) ...[
                    const SizedBox(height: 80),
                    AppErrorWidget(
                      message: _error!,
                      onRetry: _loadData,
                    ),
                  ] else ...[
                    // Header card
                    _buildHeaderCard(user?.fullName ?? user?.username ?? 'Utilisateur',
                        user?.displayRole ?? user?.role ?? ''),
                    const SizedBox(height: 20),
                    // KPI Cards grid
                    _buildKpiSection(),
                    const SizedBox(height: 20),
                    // Action buttons
                    _buildActionButtons(),
                    const SizedBox(height: 24),
                    // Quick access
                    _buildQuickAccess(),
                    const SizedBox(height: 24),
                    // Recent alertes
                    if (_recentAlertes.isNotEmpty) ...[
                      _buildSectionHeader(
                        'Alertes récentes',
                        onViewAll: () => context.go('/alertes'),
                      ),
                      const SizedBox(height: 12),
                      ..._recentAlertes.map((a) => Padding(
                            padding: const EdgeInsets.only(bottom: 6),
                            child: TransactionItem(
                              icon: Icons.warning_amber_rounded,
                              iconColor: a.isEscaladee ? AppColors.warning : AppColors.error,
                              title: a.nomClient ?? 'Prêt #${a.idPret}',
                              subtitle: a.message ?? 'Alerte active',
                              trailing: StatusBadge(statut: a.statut, small: true),
                              onTap: () => context.go('/alertes/${a.id}'),
                            ),
                          )),
                      const SizedBox(height: 16),
                    ],
                    // Recent prets
                    if (_recentPrets.isNotEmpty) ...[
                      _buildSectionHeader(
                        'Prêts en retard',
                        onViewAll: () => context.go('/prets'),
                      ),
                      const SizedBox(height: 12),
                      ..._recentPrets.map((p) => Padding(
                            padding: const EdgeInsets.only(bottom: 6),
                            child: TransactionItem(
                              icon: Icons.account_balance_wallet_rounded,
                              iconColor: AppColors.teal,
                              title: p.reference,
                              subtitle: p.nomClient ?? 'Client inconnu',
                              amount: _formatCurrency(p.montantRestant ?? p.montantInitial),
                              trailing: p.isEnRetard
                                  ? RetardBadge(joursRetard: p.joursRetard ?? 0)
                                  : null,
                              onTap: () => context.go('/prets/${p.idPret}'),
                            ),
                          )),
                    ],
                    const SizedBox(height: 24),
                  ],
                ]),
              ),
            ),
          ],
        ),
      ),
      bottomNavigationBar: const AppBottomNav(currentIndex: 0),
    );
  }

  Widget _buildHeaderCard(String name, String role) {
    final kpi = _kpi ?? KpiSummary.empty();
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [AppColors.darkSurface, AppColors.darkSurfaceRaised],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.darkBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [AppColors.navy, AppColors.teal],
                  ),
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: Text(
                    name.isNotEmpty ? name[0].toUpperCase() : 'U',
                    style: const TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 20,
                      fontWeight: FontWeight.w800,
                      color: Colors.white,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Bonjour, $name',
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                      ),
                    ),
                    Text(
                      role,
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: kpi.nbAlertesActives > 0
                      ? AppColors.error.withOpacity(0.15)
                      : AppColors.success.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      kpi.nbAlertesActives > 0
                          ? Icons.warning_amber_rounded
                          : Icons.check_circle_outline,
                      size: 14,
                      color: kpi.nbAlertesActives > 0
                          ? AppColors.error
                          : AppColors.success,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      '${kpi.nbAlertesActives}',
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: kpi.nbAlertesActives > 0
                            ? AppColors.error
                            : AppColors.success,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          const Text(
            'Aperçu du portefeuille',
            style: TextStyle(
              fontFamily: 'Inter',
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: AppColors.textSecondary,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            _formatCurrency(kpi.totalCollectes),
            style: const TextStyle(
              fontFamily: 'Inter',
              fontSize: 28,
              fontWeight: FontWeight.w800,
              color: Colors.white,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            '${kpi.nbCollectes} collecte${kpi.nbCollectes > 1 ? 's' : ''} ce mois',
            style: const TextStyle(
              fontFamily: 'Inter',
              fontSize: 12,
              color: AppColors.textSecondary,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildKpiSection() {
    final kpi = _kpi ?? KpiSummary.empty();
    return GridView.count(
      crossAxisCount: 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      crossAxisSpacing: 12,
      mainAxisSpacing: 12,
      childAspectRatio: 1.3,
      children: [
        KpiCard(
          icon: Icons.trending_up_rounded,
          label: 'Encours +30j',
          value: kpi.encoursPar30,
          isCurrency: true,
          iconColor: AppColors.warning,
          iconBg: AppColors.warning.withOpacity(0.15),
        ),
        KpiCard(
          icon: Icons.trending_down_rounded,
          label: 'Encours +90j',
          value: kpi.encoursPar90,
          isCurrency: true,
          iconColor: AppColors.error,
          iconBg: AppColors.error.withOpacity(0.15),
        ),
        KpiCard(
          icon: Icons.receipt_long_rounded,
          label: 'Nb Collectes',
          value: kpi.nbCollectes,
          iconColor: AppColors.teal,
          iconBg: AppColors.teal.withOpacity(0.15),
        ),
        KpiCard(
          icon: Icons.notifications_active_rounded,
          label: 'Alertes actives',
          value: kpi.nbAlertesActives,
          iconColor: AppColors.error,
          iconBg: AppColors.error.withOpacity(0.15),
        ),
      ],
    );
  }

  Widget _buildActionButtons() {
    return Row(
      children: [
        Expanded(
          child: GestureDetector(
            onTap: () => context.go('/prets'),
            child: Container(
              height: 48,
              decoration: BoxDecoration(
                border: Border.all(color: AppColors.darkBorder),
                borderRadius: BorderRadius.circular(12),
                color: AppColors.darkSurfaceRaised,
              ),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.account_balance_wallet_rounded,
                      color: AppColors.textSecondary, size: 18),
                  SizedBox(width: 8),
                  Text(
                    'Prêts actifs',
                    style: TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: GestureDetector(
            onTap: () => context.go('/alertes'),
            child: Container(
              height: 48,
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [AppColors.gold, AppColors.goldLight],
                ),
                borderRadius: BorderRadius.circular(12),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.gold.withOpacity(0.3),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.warning_amber_rounded,
                      color: AppColors.navyDeep, size: 18),
                  SizedBox(width: 8),
                  Text(
                    'Alertes',
                    style: TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 13,
                      fontWeight: FontWeight.w700,
                      color: AppColors.navyDeep,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildQuickAccess() {
    final kpi = _kpi;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Accès rapide',
          style: TextStyle(
            fontFamily: 'Inter',
            fontSize: 16,
            fontWeight: FontWeight.w700,
            color: Colors.white,
          ),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: QuickAccessTile(
                icon: Icons.account_balance_wallet_rounded,
                label: 'Prêts',
                onTap: () => context.go('/prets'),
                color: AppColors.teal,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: QuickAccessTile(
                icon: Icons.warning_amber_rounded,
                label: 'Alertes',
                onTap: () => context.go('/alertes'),
                color: AppColors.warning,
                badge: kpi?.nbAlertesActives,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: QuickAccessTile(
                icon: Icons.people_rounded,
                label: 'Clients',
                onTap: () => context.go('/clients'),
                color: AppColors.navy,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildSectionHeader(String title, {VoidCallback? onViewAll}) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          title,
          style: const TextStyle(
            fontFamily: 'Inter',
            fontSize: 16,
            fontWeight: FontWeight.w700,
            color: Colors.white,
          ),
        ),
        if (onViewAll != null)
          GestureDetector(
            onTap: onViewAll,
            child: const Text(
              'Voir tout →',
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: AppColors.gold,
              ),
            ),
          ),
      ],
    );
  }
}
