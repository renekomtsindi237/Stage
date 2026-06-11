import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/models/alerte.dart';
import '../../core/services/alerte_service.dart';
import '../../widgets/app_bottom_nav.dart';
import '../../widgets/empty_state.dart';
import '../../widgets/error_widget.dart';
import '../../widgets/skeleton_loader.dart';
import '../../widgets/status_badge.dart';

class AlertesListScreen extends StatefulWidget {
  const AlertesListScreen({super.key});

  @override
  State<AlertesListScreen> createState() => _AlertesListScreenState();
}

class _AlertesListScreenState extends State<AlertesListScreen> {
  final _scrollController = ScrollController();
  String _selectedStatut = '';
  List<Alerte> _alertes = [];
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;
  int _currentPage = 0;
  bool _hasMore = true;
  int _totalElements = 0;
  late final AlerteService _alerteService;

  final List<Map<String, String>> _filters = [
    {'label': 'Toutes', 'value': ''},
    {'label': 'Actives', 'value': 'ACTIVE'},
    {'label': 'Escaladées', 'value': 'ESCALADEE'},
    {'label': 'Clôturées', 'value': 'CLOTUREE'},
  ];

  @override
  void initState() {
    super.initState();
    _alerteService = context.read<AlerteService>();
    _loadAlertes(reset: true);
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
            _scrollController.position.maxScrollExtent - 200 &&
        _hasMore &&
        !_loadingMore) {
      _loadAlertes(reset: false);
    }
  }

  Future<void> _loadAlertes({bool reset = true}) async {
    if (reset) {
      setState(() {
        _loading = true;
        _error = null;
        _currentPage = 0;
        _hasMore = true;
      });
    } else {
      if (_loadingMore) return;
      setState(() => _loadingMore = true);
    }

    try {
      final page = await _alerteService.getAlertes(
        statut: _selectedStatut.isEmpty ? null : _selectedStatut,
        page: reset ? 0 : _currentPage,
        size: 20,
      );

      setState(() {
        if (reset) {
          _alertes = page.content;
        } else {
          _alertes.addAll(page.content);
        }
        _totalElements = page.totalElements;
        _currentPage = (reset ? 0 : _currentPage) + 1;
        _hasMore = !page.last;
        _loading = false;
        _loadingMore = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
        _loadingMore = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.darkBg,
      appBar: AppBar(
        title: const Text('Alertes'),
        backgroundColor: AppColors.darkBg,
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: Center(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.error.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '$_totalElements',
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: AppColors.error,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // Filter chips
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: _filters.map((f) {
                final isSelected = _selectedStatut == f['value'];
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: FilterChip(
                    label: Text(f['label']!),
                    selected: isSelected,
                    onSelected: (selected) {
                      setState(() => _selectedStatut = f['value']!);
                      _loadAlertes(reset: true);
                    },
                    selectedColor: AppColors.gold.withOpacity(0.2),
                    checkmarkColor: AppColors.gold,
                    labelStyle: TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: isSelected ? AppColors.gold : AppColors.textSecondary,
                    ),
                    side: BorderSide(
                      color: isSelected ? AppColors.gold : AppColors.darkBorder,
                    ),
                    backgroundColor: AppColors.darkSurfaceRaised,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(20),
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
          // List
          Expanded(
            child: _loading
                ? ListView.builder(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemCount: 8,
                    itemBuilder: (_, __) => const Padding(
                      padding: EdgeInsets.only(bottom: 8),
                      child: SkeletonListItem(),
                    ),
                  )
                : _error != null
                    ? AppErrorWidget(
                        message: _error!,
                        onRetry: () => _loadAlertes(reset: true))
                    : _alertes.isEmpty
                        ? EmptyState(
                            icon: Icons.notifications_off_outlined,
                            title: 'Aucune alerte trouvée',
                            subtitle: 'Aucune alerte pour les filtres sélectionnés',
                            onRetry: () => _loadAlertes(reset: true),
                          )
                        : RefreshIndicator(
                            onRefresh: () => _loadAlertes(reset: true),
                            color: AppColors.gold,
                            backgroundColor: AppColors.darkSurface,
                            child: ListView.builder(
                              controller: _scrollController,
                              padding: const EdgeInsets.symmetric(horizontal: 16),
                              itemCount:
                                  _alertes.length + (_loadingMore ? 1 : 0),
                              itemBuilder: (context, index) {
                                if (index == _alertes.length) {
                                  return const Padding(
                                    padding: EdgeInsets.all(16),
                                    child: Center(
                                      child: CircularProgressIndicator(
                                        color: AppColors.gold,
                                        strokeWidth: 2,
                                      ),
                                    ),
                                  );
                                }
                                return _buildAlerteItem(_alertes[index]);
                              },
                            ),
                          ),
          ),
        ],
      ),
      bottomNavigationBar: const AppBottomNav(currentIndex: 2),
    );
  }

  Widget _buildAlerteItem(Alerte alerte) {
    Color borderColor;
    Color iconColor;

    if (alerte.isActive) {
      borderColor = AppColors.error;
      iconColor = AppColors.error;
    } else if (alerte.isEscaladee) {
      borderColor = AppColors.warning;
      iconColor = AppColors.warning;
    } else {
      borderColor = AppColors.textMuted;
      iconColor = AppColors.textMuted;
    }

    return GestureDetector(
      onTap: () => context.go('/alertes/${alerte.id}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        decoration: BoxDecoration(
          color: AppColors.darkSurface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.darkBorder),
        ),
        child: Row(
          children: [
            // Left color border
            Container(
              width: 4,
              height: 80,
              decoration: BoxDecoration(
                color: borderColor,
                borderRadius: const BorderRadius.only(
                  topLeft: Radius.circular(14),
                  bottomLeft: Radius.circular(14),
                ),
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: iconColor.withOpacity(0.15),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(
                        alerte.isActive
                            ? Icons.warning_amber_rounded
                            : alerte.isEscaladee
                                ? Icons.priority_high_rounded
                                : Icons.check_circle_outline,
                        color: iconColor,
                        size: 20,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            alerte.nomClient ?? 'Prêt #${alerte.idPret}',
                            style: const TextStyle(
                              fontFamily: 'Inter',
                              fontSize: 14,
                              fontWeight: FontWeight.w700,
                              color: Colors.white,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 2),
                          Text(
                            alerte.message ?? 'Alerte de recouvrement',
                            style: const TextStyle(
                              fontFamily: 'Inter',
                              fontSize: 12,
                              color: AppColors.textSecondary,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          if (alerte.joursRetard != null) ...[
                            const SizedBox(height: 4),
                            Text(
                              '${alerte.joursRetard} j de retard',
                              style: TextStyle(
                                fontFamily: 'Inter',
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                                color: iconColor,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        StatusBadge(statut: alerte.statut, small: true),
                        const SizedBox(height: 4),
                        const Icon(Icons.chevron_right,
                            color: AppColors.textMuted, size: 16),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
