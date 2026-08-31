import 'package:flutter/material.dart';
import 'package:microrecouv/l10n/app_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/models/collecte_locale.dart';
import '../../core/providers/locale_provider.dart';
import '../../core/providers/sync_provider.dart';
import '../../core/services/connectivity_service.dart';
import '../../core/services/sync_service.dart';
import '../../widgets/app_bottom_nav.dart';
import '../../widgets/connectivity_banner.dart';
import '../../widgets/lang_switch_button.dart';

class HistoriqueJourScreen extends StatefulWidget {
  const HistoriqueJourScreen({super.key});
  @override
  State<HistoriqueJourScreen> createState() => _HistoriqueJourScreenState();
}

class _HistoriqueJourScreenState extends State<HistoriqueJourScreen> {
  List<CollecteLocale> _pending = [];
  List<CollecteLocale> _synced = [];
  SyncResult? _lastSync;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _onSyncPressed() async {
    final l10n = AppL10n.of(context);
    final sync = context.read<SyncProvider>();
    if (sync.syncing) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.historiqueSyncing),
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }

    final online = await context.read<ConnectivityService>().isConnected();
    if (!mounted) return;
    if (!online) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.offlineBanner),
          behavior: SnackBarBehavior.floating,
          backgroundColor: const Color(0xFFB71C1C),
        ),
      );
      return;
    }

    final result = await sync.syncNow();
    if (!mounted) return;
    await _load();
    if (!mounted) return;

    if (result != null) {
      context.push('/collectes/sync-result', extra: result);
      return;
    }

    final err = sync.syncError ?? l10n.loginNetworkError;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(err),
        behavior: SnackBarBehavior.floating,
        backgroundColor: const Color(0xFFDC2626),
      ),
    );
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final svc = context.read<SyncService>();
    final pending = await svc.getPendingCollectes();
    final synced = await svc.getSyncedCollectes();
    final last = await svc.getLastSyncResult();
    if (mounted) {
      setState(() {
        _pending = pending;
        _synced = synced;
        _lastSync = last;
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppL10n.of(context);
    final locale = context.watch<LocaleProvider>();
    final fmt = NumberFormat('#,###', 'fr_FR');
    final timeFmt = DateFormat('HH:mm', locale.isFrench ? 'fr_FR' : 'en_US');
    final dateLocale = locale.isFrench ? 'fr_FR' : 'en_US';
    final today = DateFormat('EEEE d MMMM yyyy', dateLocale).format(DateTime.now());
    final sync = context.watch<SyncProvider>();
    if (!_loading && sync.pendingCount != _pending.length) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _load();
      });
    }
    final total = _pending.fold<double>(0, (s, c) => s + c.montantCollecte);

    return Scaffold(
      backgroundColor: context.bg,
      appBar: AppBar(
        title: Text(l10n.historiqueTitle),
        backgroundColor: context.bg,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () => context.go('/dashboard'),
        ),
        actions: [
          const Padding(
            padding: EdgeInsets.only(right: 8),
            child: LangSwitchButton(onDark: false),
          ),
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh_rounded)),
        ],
      ),
      body: Column(
        children: [
          const ConnectivityBanner(),
          Expanded(
            child: _loading
          ? const Center(child: CircularProgressIndicator(color: AppColors.gold))
          : RefreshIndicator(
              onRefresh: _load,
              color: AppColors.gold,
              backgroundColor: context.surface,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Text(
                    today[0].toUpperCase() + today.substring(1),
                    style: TextStyle(
                      fontFamily: 'Inter',
                      fontSize: 13,
                      color: context.textSec,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: context.cardBox,
                    child: Row(children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              l10n.historiquePendingLabel,
                              style: TextStyle(fontFamily: 'Inter', fontSize: 12, color: context.textSec),
                            ),
                            Text(
                              '${_pending.length}',
                              style: TextStyle(
                                fontFamily: 'Inter',
                                fontSize: 30,
                                fontWeight: FontWeight.w900,
                                color: context.text,
                              ),
                            ),
                          ],
                        ),
                      ),
                      Container(width: 1, height: 48, color: context.border),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.only(left: 18),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                l10n.historiqueTotalAmount,
                                style: TextStyle(fontFamily: 'Inter', fontSize: 12, color: context.textSec),
                              ),
                              Text(
                                '${fmt.format(total.toInt())} F',
                                style: const TextStyle(
                                  fontFamily: 'Inter',
                                  fontSize: 18,
                                  fontWeight: FontWeight.w800,
                                  color: AppColors.gold,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ]),
                  ),
                  const SizedBox(height: 14),
                  if (sync.syncError != null && sync.syncError!.isNotEmpty) ...[
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      decoration: BoxDecoration(
                        color: AppColors.error.withOpacity(0.08),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: AppColors.error.withOpacity(0.3)),
                      ),
                      child: Text(
                        sync.syncError!,
                        style: const TextStyle(
                          fontFamily: 'Inter',
                          fontSize: 12,
                          fontWeight: FontWeight.w600,
                          color: AppColors.error,
                        ),
                      ),
                    ),
                    const SizedBox(height: 10),
                  ],
                  if (_pending.isNotEmpty)
                    SizedBox(
                      height: 48,
                      child: ElevatedButton.icon(
                        onPressed: sync.syncing ? null : _onSyncPressed,
                        icon: sync.syncing
                            ? const SizedBox(
                                width: 18,
                                height: 18,
                                child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                              )
                            : const Icon(Icons.sync_rounded),
                        label: Text(
                          sync.syncing ? l10n.historiqueSyncing : l10n.historiqueSyncNow,
                          style: const TextStyle(
                            fontFamily: 'Inter',
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.teal,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ),
                  if (_lastSync != null) ...[
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      decoration: BoxDecoration(
                        color: AppColors.success.withOpacity(0.08),
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(color: AppColors.success.withOpacity(0.25)),
                      ),
                      child: Row(children: [
                        const Icon(Icons.check_circle_outline_rounded, color: AppColors.success, size: 15),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            l10n.historiqueLastSync(
                              _lastSync!.acceptees,
                              _lastSync!.totalRecu,
                              timeFmt.format(_lastSync!.syncedAt),
                            ),
                            style: const TextStyle(
                              fontFamily: 'Inter',
                              fontSize: 12,
                              color: AppColors.success,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ]),
                    ),
                  ],
                  const SizedBox(height: 20),
                  if (_pending.isEmpty)
                    Container(
                      padding: const EdgeInsets.all(32),
                      decoration: context.cardBoxR(12),
                      child: Column(children: [
                        Icon(Icons.inbox_rounded, size: 48, color: context.textMut),
                        const SizedBox(height: 12),
                        Text(
                          l10n.historiqueEmptyTitle,
                          style: TextStyle(
                            fontFamily: 'Inter',
                            fontSize: 14,
                            color: context.textSec,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          l10n.historiqueEmptySubtitle,
                          style: TextStyle(fontFamily: 'Inter', fontSize: 12, color: context.textMut),
                        ),
                      ]),
                    )
                  else ...[
                    Text(
                      l10n.historiqueListTitle(_pending.length),
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: context.text,
                      ),
                    ),
                    const SizedBox(height: 10),
                    ..._pending.map((c) {
                      final d = DateTime.tryParse(c.dateCollecte);
                      return Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        padding: const EdgeInsets.all(14),
                        decoration: context.cardBoxR(12),
                        child: Row(children: [
                          Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              color: AppColors.teal.withOpacity(0.1),
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.account_balance_wallet_rounded,
                              color: AppColors.teal,
                              size: 19,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  c.nomClient ?? c.clientIdExterne,
                                  style: TextStyle(
                                    fontFamily: 'Inter',
                                    fontSize: 13,
                                    fontWeight: FontWeight.w700,
                                    color: context.text,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                Text(
                                  c.canalPaiement,
                                  style: TextStyle(fontFamily: 'Inter', fontSize: 11, color: context.textSec),
                                ),
                              ],
                            ),
                          ),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                '${fmt.format(c.montantCollecte.toInt())} F',
                                style: const TextStyle(
                                  fontFamily: 'Inter',
                                  fontSize: 14,
                                  fontWeight: FontWeight.w800,
                                  color: AppColors.gold,
                                ),
                              ),
                              if (d != null)
                                Text(
                                  DateFormat('HH:mm').format(d),
                                  style: TextStyle(fontFamily: 'Inter', fontSize: 11, color: context.textSec),
                                ),
                            ],
                          ),
                          const SizedBox(width: 8),
                          Container(
                            width: 8,
                            height: 8,
                            decoration: BoxDecoration(
                              color: c.lastCode == 'CONFLIT' || c.lastCode == 'ERREUR'
                                  ? AppColors.error
                                  : AppColors.warning,
                              shape: BoxShape.circle,
                            ),
                          ),
                        ]),
                      );
                    }),
                  ],
                  if (_synced.isNotEmpty) ...[
                    const SizedBox(height: 24),
                    Text(
                      l10n.historiqueSyncedTitle,
                      style: TextStyle(
                        fontFamily: 'Inter',
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: context.text,
                      ),
                    ),
                    const SizedBox(height: 10),
                    ..._synced.take(20).map((c) {
                      return Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        padding: const EdgeInsets.all(14),
                        decoration: context.cardBoxR(12),
                        child: Row(children: [
                          Container(
                            width: 40,
                            height: 40,
                            decoration: BoxDecoration(
                              color: AppColors.success.withOpacity(0.1),
                              shape: BoxShape.circle,
                            ),
                            child: const Icon(
                              Icons.cloud_done_rounded,
                              color: AppColors.success,
                              size: 19,
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  c.nomClient ?? c.clientIdExterne,
                                  style: TextStyle(
                                    fontFamily: 'Inter',
                                    fontSize: 13,
                                    fontWeight: FontWeight.w700,
                                    color: context.text,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                Text(
                                  '${c.canalPaiement} · ${c.lastCode ?? 'SUCCESS'}',
                                  style: TextStyle(fontFamily: 'Inter', fontSize: 11, color: context.textSec),
                                ),
                              ],
                            ),
                          ),
                          Text(
                            '${fmt.format(c.montantCollecte.toInt())} F',
                            style: const TextStyle(
                              fontFamily: 'Inter',
                              fontSize: 14,
                              fontWeight: FontWeight.w800,
                              color: AppColors.gold,
                            ),
                          ),
                        ]),
                      );
                    }),
                  ],
                  const SizedBox(height: 24),
                ],
              ),
            ),
          ),
        ],
      ),
      bottomNavigationBar: const AppBottomNav(currentIndex: 3),
    );
  }
}
