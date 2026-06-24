import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/models/alerte.dart';
import '../../core/services/alerte_service.dart';

class AlertesScreen extends StatefulWidget {
  const AlertesScreen({super.key});

  @override
  State<AlertesScreen> createState() => _AlertesScreenState();
}

class _AlertesScreenState extends State<AlertesScreen> {
  List<Alerte> _alertes = [];
  bool _loading = true;
  bool _loadingMore = false;
  String? _error;
  String? _selectedStatut;
  int _page = 0;
  bool _hasMore = true;
  final _scrollController = ScrollController();

  static const _filtres = [
    (label: 'Toutes', value: null as String?),
    (label: 'Actives', value: 'ACTIVE'),
    (label: 'Escaladées', value: 'ESCALADEE'),
    (label: 'Traitées', value: 'TRAITEE'),
    (label: 'Clôturées', value: 'CLOTUREE'),
  ];

  @override
  void initState() {
    super.initState();
    _load(reset: true);
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
        !_loadingMore &&
        _hasMore) {
      _loadMore();
    }
  }

  Future<void> _load({bool reset = false}) async {
    if (!mounted) return;
    final page = reset ? 0 : _page;

    if (reset) {
      setState(() {
        _loading = true;
        _error = null;
        _page = 0;
        _hasMore = true;
      });
    }

    try {
      final response = await context.read<AlerteService>().getAlertes(
            statut: _selectedStatut,
            page: page,
            size: 20,
          );
      if (!mounted) return;
      setState(() {
        if (reset) {
          _alertes = response.content;
        } else {
          _alertes.addAll(response.content);
        }
        _page = page + 1;
        _hasMore = !response.last;
        _loading = false;
        _loadingMore = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e.toString();
        _loading = false;
        _loadingMore = false;
      });
    }
  }

  Future<void> _loadMore() async {
    if (!mounted || _loadingMore) return;
    setState(() => _loadingMore = true);
    await _load();
  }

  void _setFilter(String? statut) {
    if (_selectedStatut == statut) return;
    setState(() => _selectedStatut = statut);
    _load(reset: true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: context.bg,
      body: Column(
        children: [
          _buildTopBar(),
          _buildFilterRow(),
          Expanded(child: _buildContent()),
        ],
      ),
    );
  }

  Widget _buildTopBar() {
    return Container(
      color: AppColors.navyDark,
      padding: EdgeInsets.only(
        top: MediaQuery.of(context).padding.top + 8,
        bottom: 12,
        left: 4,
        right: 16,
      ),
      child: Row(
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back_rounded, color: Colors.white),
            onPressed: () => context.go('/dashboard'),
          ),
          const Expanded(
            child: Text(
              'Alertes',
              style: TextStyle(
                fontFamily: 'Inter',
                fontSize: 17,
                fontWeight: FontWeight.w700,
                color: Colors.white,
              ),
            ),
          ),
          if (!_loading && _alertes.isNotEmpty)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: AppColors.error.withOpacity(0.2),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(
                '${_alertes.length}',
                style: const TextStyle(
                  fontFamily: 'Inter',
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: AppColors.error,
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildFilterRow() {
    return Container(
      color: context.surface,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: _filtres.map((f) {
            final selected = _selectedStatut == f.value;
            return Padding(
              padding: const EdgeInsets.only(right: 8),
              child: GestureDetector(
                onTap: () => _setFilter(f.value),
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 150),
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
                  decoration: BoxDecoration(
                    color: selected
                        ? AppColors.gold.withOpacity(0.15)
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(
                      color: selected ? AppColors.gold : context.border,
                      width: selected ? 1.5 : 1,
                    ),
                  ),
                  child: Text(
                    f.label,
                    style: TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 13,
                      fontWeight:
                          selected ? FontWeight.w700 : FontWeight.w400,
                      color: selected ? AppColors.gold : context.textSec,
                    ),
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  Widget _buildContent() {
    if (_loading) {
      return const Center(
        child: CircularProgressIndicator(color: AppColors.gold),
      );
    }
    if (_error != null && _alertes.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline, size: 52, color: context.textSec),
              const SizedBox(height: 14),
              Text(
                'Impossible de charger les alertes',
                style: TextStyle(fontFamily: 'Inter', fontSize: 14, color: context.textSec),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 18),
              TextButton(
                onPressed: () => _load(reset: true),
                child: const Text(
                  'Réessayer',
                  style: TextStyle(
                    color: AppColors.gold,
                    fontFamily: 'Inter',
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }
    if (_alertes.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.notifications_off_outlined, size: 52, color: context.textSec),
            const SizedBox(height: 14),
            Text(
              'Aucune alerte',
              style: TextStyle(fontFamily: 'Inter', fontSize: 14, color: context.textSec),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: () => _load(reset: true),
      color: AppColors.gold,
      backgroundColor: context.surface,
      child: ListView.builder(
        controller: _scrollController,
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        itemCount: _alertes.length + (_loadingMore ? 1 : 0),
        itemBuilder: (_, i) {
          if (i == _alertes.length) {
            return const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Center(
                child: CircularProgressIndicator(
                  color: AppColors.gold,
                  strokeWidth: 2,
                ),
              ),
            );
          }
          return Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: _buildItem(_alertes[i]),
          );
        },
      ),
    );
  }

  Widget _buildItem(Alerte a) {
    final accentColor = a.isEscaladee
        ? AppColors.error
        : a.isActive
            ? AppColors.warning
            : context.textSec;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: context.cardBoxR(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 38,
                height: 38,
                decoration: BoxDecoration(
                  color: accentColor.withOpacity(0.12),
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  a.isEscaladee
                      ? Icons.warning_rounded
                      : Icons.notifications_rounded,
                  color: accentColor,
                  size: 17,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      a.nomClient ?? 'Client inconnu',
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: context.text,
                      ),
                    ),
                    if (a.referencePret != null)
                      Text(
                        a.referencePret!,
                        style: TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 11,
                          color: context.textSec,
                        ),
                      ),
                  ],
                ),
              ),
              _statutChip(a),
            ],
          ),
          if (a.message != null) ...[
            const SizedBox(height: 8),
            Text(
              a.message!,
              style: TextStyle(fontFamily: 'Inter', fontSize: 13, color: context.textSec),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ],
          if ((a.joursRetard != null && a.joursRetard! > 0) || a.montantDu != null) ...[
            const SizedBox(height: 10),
            Row(
              children: [
                if (a.joursRetard != null && a.joursRetard! > 0) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: AppColors.error.withOpacity(0.12),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      '${a.joursRetard}j retard',
                      style: const TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: AppColors.error,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                if (a.montantDu != null)
                  Text(
                    '${NumberFormat('#,###', 'fr_FR').format(a.montantDu!.toInt())} FCFA dû',
                    style: TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: context.text,
                    ),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _statutChip(Alerte a) {
    final Color color;
    if (a.isEscaladee) {
      color = AppColors.error;
    } else if (a.isActive) {
      color = AppColors.warning;
    } else if (a.isCloturee) {
      color = AppColors.success;
    } else {
      color = context.textSec;
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(
        a.displayStatut,
        style: TextStyle(
          fontFamily: 'Inter',
          fontSize: 10,
          fontWeight: FontWeight.w700,
          color: color,
        ),
      ),
    );
  }
}
