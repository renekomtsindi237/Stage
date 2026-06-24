import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import '../../core/constants/app_colors.dart';
import '../../core/constants/theme_helper.dart';
import '../../core/models/collecte_locale.dart';
import '../../core/providers/sync_provider.dart';
import '../../core/services/sync_service.dart';
import '../../widgets/app_bottom_nav.dart';

class HistoriqueJourScreen extends StatefulWidget {
  const HistoriqueJourScreen({super.key});
  @override
  State<HistoriqueJourScreen> createState() => _HistoriqueJourScreenState();
}

class _HistoriqueJourScreenState extends State<HistoriqueJourScreen> {
  List<CollecteLocale> _pending = [];
  SyncResult? _lastSync;
  bool _loading = true;

  @override
  void initState() { super.initState(); _load(); }

  Future<void> _load() async {
    setState(() => _loading = true);
    final svc = context.read<SyncService>();
    final pending = await svc.getPendingCollectes();
    final last = await svc.getLastSyncResult();
    if (mounted) setState(() { _pending = pending; _lastSync = last; _loading = false; });
  }

  @override
  Widget build(BuildContext context) {
    final fmt = NumberFormat('#,###', 'fr_FR');
    final timeFmt = DateFormat('HH:mm', 'fr_FR');
    final today = DateFormat('EEEE d MMMM yyyy', 'fr_FR').format(DateTime.now());
    final sync = context.watch<SyncProvider>();
    final total = _pending.fold<double>(0, (s, c) => s + c.montantCollecte);

    return Scaffold(
      backgroundColor: context.bg,
      appBar: AppBar(
        title: const Text('Historique du jour'),
        backgroundColor: context.bg,
        leading: IconButton(icon: const Icon(Icons.arrow_back_rounded), onPressed: () => context.go('/dashboard')),
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh_rounded))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: AppColors.gold))
          : RefreshIndicator(
              onRefresh: _load,
              color: AppColors.gold,
              backgroundColor: context.surface,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Text(today[0].toUpperCase() + today.substring(1),
                      style: TextStyle(fontFamily: 'Inter', fontSize: 13, color: context.textSec, fontWeight: FontWeight.w500)),
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: context.cardBox,
                    child: Row(children: [
                      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text('En attente', style: TextStyle(fontFamily: 'Inter', fontSize: 12, color: context.textSec)),
                        Text('${_pending.length}', style: TextStyle(fontFamily: 'Inter', fontSize: 30, fontWeight: FontWeight.w900, color: context.text)),
                      ])),
                      Container(width: 1, height: 48, color: context.border),
                      Expanded(child: Padding(padding: const EdgeInsets.only(left: 18), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text('Montant total', style: TextStyle(fontFamily: 'Inter', fontSize: 12, color: context.textSec)),
                        Text('${fmt.format(total.toInt())} F', style: const TextStyle(fontFamily: 'Inter', fontSize: 18, fontWeight: FontWeight.w800, color: AppColors.gold)),
                      ]))),
                    ]),
                  ),
                  const SizedBox(height: 14),
                  if (_pending.isNotEmpty) SizedBox(
                    height: 48,
                    child: ElevatedButton.icon(
                      onPressed: sync.syncing ? null : () => sync.syncNow(),
                      icon: sync.syncing
                          ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                          : const Icon(Icons.sync_rounded),
                      label: Text(sync.syncing ? 'Synchronisation...' : 'Synchroniser maintenant',
                          style: const TextStyle(fontFamily: 'Inter', fontSize: 14, fontWeight: FontWeight.w700)),
                      style: ElevatedButton.styleFrom(backgroundColor: AppColors.teal, foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12))),
                    ),
                  ),
                  if (_lastSync != null) ...[
                    const SizedBox(height: 10),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                      decoration: BoxDecoration(color: AppColors.success.withOpacity(0.08), borderRadius: BorderRadius.circular(10),
                          border: Border.all(color: AppColors.success.withOpacity(0.25))),
                      child: Row(children: [
                        const Icon(Icons.check_circle_outline_rounded, color: AppColors.success, size: 15),
                        const SizedBox(width: 8),
                        Expanded(child: Text(
                          'Dernière sync : ${_lastSync!.acceptees}/${_lastSync!.totalRecu} — ${timeFmt.format(_lastSync!.syncedAt)}',
                          style: const TextStyle(fontFamily: 'Inter', fontSize: 12, color: AppColors.success, fontWeight: FontWeight.w600),
                        )),
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
                        Text('Aucune collecte en attente', style: TextStyle(fontFamily: 'Inter', fontSize: 14, color: context.textSec, fontWeight: FontWeight.w600)),
                        const SizedBox(height: 4),
                        Text('Toutes vos collectes sont synchronisées.', style: TextStyle(fontFamily: 'Inter', fontSize: 12, color: context.textMut)),
                      ]),
                    )
                  else ...[
                    Text('Collectes (${_pending.length})', style: TextStyle(fontFamily: 'Inter', fontSize: 14, fontWeight: FontWeight.w700, color: context.text)),
                    const SizedBox(height: 10),
                    ..._pending.map((c) {
                      final d = DateTime.tryParse(c.dateCollecte);
                      return Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        padding: const EdgeInsets.all(14),
                        decoration: context.cardBoxR(12),
                        child: Row(children: [
                          Container(width: 40, height: 40,
                              decoration: BoxDecoration(color: AppColors.teal.withOpacity(0.1), shape: BoxShape.circle),
                              child: const Icon(Icons.account_balance_wallet_rounded, color: AppColors.teal, size: 19)),
                          const SizedBox(width: 12),
                          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text(c.nomClient ?? c.clientIdExterne, style: TextStyle(fontFamily: 'Inter', fontSize: 13, fontWeight: FontWeight.w700, color: context.text),
                                maxLines: 1, overflow: TextOverflow.ellipsis),
                            Text(c.canalPaiement, style: TextStyle(fontFamily: 'Inter', fontSize: 11, color: context.textSec)),
                          ])),
                          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
                            Text('${fmt.format(c.montantCollecte.toInt())} F',
                                style: const TextStyle(fontFamily: 'Inter', fontSize: 14, fontWeight: FontWeight.w800, color: AppColors.gold)),
                            if (d != null) Text(DateFormat('HH:mm').format(d),
                                style: TextStyle(fontFamily: 'Inter', fontSize: 11, color: context.textSec)),
                          ]),
                          const SizedBox(width: 8),
                          Container(width: 8, height: 8, decoration: const BoxDecoration(color: AppColors.warning, shape: BoxShape.circle)),
                        ]),
                      );
                    }),
                  ],
                  const SizedBox(height: 24),
                ],
              ),
            ),
      bottomNavigationBar: const AppBottomNav(currentIndex: 3),
    );
  }
}
