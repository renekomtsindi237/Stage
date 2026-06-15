import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/models/pret.dart';
import '../../core/services/pret_service.dart';
import '../../widgets/app_bottom_nav.dart';
import '../../widgets/empty_state.dart';
import '../../widgets/error_widget.dart';
import '../../widgets/skeleton_loader.dart';
import '../../widgets/status_badge.dart';

class PretsListScreen extends StatefulWidget {
  const PretsListScreen({super.key});

  @override
  State<PretsListScreen> createState() => _PretsListScreenState();
}

class _PretsListScreenState extends State<PretsListScreen> {
  final _searchController = TextEditingController();
  final _scrollController = ScrollController();

  String _selectedStatut = '';
  List<Pret> _prets = [];
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;
  int _currentPage = 0;
  bool _hasMore = true;
  int _totalElements = 0;

  late final PretService _pretService;

  final List<Map<String, String>> _filters = [
    {'label': 'Tous', 'value': ''},
    {'label': 'Actifs', 'value': 'ACTIF'},
    {'label': 'En retard', 'value': 'EN_RETARD'},
    {'label': 'SoldÃ©s', 'value': 'SOLDE'},
  ];

  @override
  void initState() {
    super.initState();
    _pretService = context.read<PretService>();
    _loadPrets(reset: true);
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
            _scrollController.position.maxScrollExtent - 200 &&
        _hasMore &&
        !_loadingMore) {
      _loadPrets(reset: false);
    }
  }

  Future<void> _loadPrets({bool reset = true}) async {
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
      final page = await _pretService.getPrets(
        statut: _selectedStatut.isEmpty ? null : _selectedStatut,
        page: reset ? 0 : _currentPage,
        size: 20,
      );

      setState(() {
        if (reset) {
          _prets = page.content;
        } else {
          _prets.addAll(page.content);
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

  String _formatCurrency(double value) {
    return NumberFormat.currency(
      locale: 'fr_CM',
      symbol: 'FCFA',
      decimalDigits: 0,
    ).format(value);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.darkBg,
      appBar: AppBar(
        title: const Text('PrÃªts'),
        backgroundColor: AppColors.darkBg,
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: Center(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.darkSurfaceRaised,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '$_totalElements',
                  style: const TextStyle(
                    fontFamily: 'Inter',
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    color: AppColors.gold,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // Search bar
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: TextField(
              controller: _searchController,
              style: const TextStyle(color: Colors.white, fontFamily: 'Inter'),
              decoration: InputDecoration(
                hintText: 'Rechercher un prÃªt...',
                prefixIcon: const Icon(Icons.search_rounded, color: AppColors.textSecondary),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        onPressed: () {
                          _searchController.clear();
                          _loadPrets(reset: true);
                        },
                        icon: const Icon(Icons.clear, color: AppColors.textSecondary, size: 18),
                      )
                    : null,
              ),
              onChanged: (_) => setState(() {}),
            ),
          ),
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
                      _loadPrets(reset: true);
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
                    ? AppErrorWidget(message: _error!, onRetry: () => _loadPrets(reset: true))
                    : _prets.isEmpty
                        ? EmptyState(
                            icon: Icons.account_balance_wallet_outlined,
                            title: 'Aucun prÃªt trouvÃ©',
                            subtitle: 'Modifiez les filtres ou rÃ©essayez',
                            onRetry: () => _loadPrets(reset: true),
                          )
                        : RefreshIndicator(
                            onRefresh: () => _loadPrets(reset: true),
                            color: AppColors.gold,
                            backgroundColor: AppColors.darkSurface,
                            child: ListView.builder(
                              controller: _scrollController,
                              padding: const EdgeInsets.symmetric(horizontal: 16),
                              itemCount: _prets.length + (_loadingMore ? 1 : 0),
                              itemBuilder: (context, index) {
                                if (index == _prets.length) {
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
                                final pret = _prets[index];
                                return _buildPretItem(pret);
                              },
                            ),
                          ),
          ),
        ],
      ),
      bottomNavigationBar: const AppBottomNav(currentIndex: 1),
    );
  }

  Widget _buildPretItem(Pret pret) {
    return GestureDetector(
      onTap: () => context.go('/prets/${pret.idPret}'),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.darkSurface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: AppColors.darkBorder),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: AppColors.teal.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: const Icon(Icons.account_balance_wallet_rounded,
                      color: AppColors.teal, size: 20),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        pret.reference,
                        style: const TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: Colors.white,
                        ),
                      ),
                      Text(
                        pret.nomClient ?? 'Client inconnu',
                        style: const TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 12,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                ),
                StatusBadge(statut: pret.statut, small: true),
              ],
            ),
            const SizedBox(height: 10),
            const Divider(color: AppColors.darkBorder, height: 1),
            const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Montant restant',
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 10,
                        color: AppColors.textMuted,
                      ),
                    ),
                    Text(
                      _formatCurrency(pret.montantRestant ?? pret.montantInitial),
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: Colors.white,
                      ),
                    ),
                  ],
                ),
                if (pret.isEnRetard)
                  RetardBadge(joursRetard: pret.joursRetard ?? 0),
                const Icon(
                  Icons.chevron_right,
                  color: AppColors.textMuted,
                  size: 18,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

