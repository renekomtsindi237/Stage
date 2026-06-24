import 'dart:convert';
import 'dart:math';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/collecte_locale.dart';
import 'api_service.dart';

class SyncService {
  static const _keyPending  = 'collectes_pending';
  static const _keyLastSync = 'last_sync_result';
  static const _keyDeviceId = 'device_id';

  final ApiService _api;

  SyncService(this._api);

  // ── Gestion locale ───────────────────────────────────────────────────────────

  Future<List<CollecteLocale>> getPendingCollectes() async {
    final prefs = await SharedPreferences.getInstance();
    final raw   = prefs.getString(_keyPending);
    if (raw == null || raw.isEmpty) return [];
    try {
      return CollecteLocale.listFromJson(raw);
    } catch (_) {
      return [];
    }
  }

  Future<void> ajouterCollecteLocale(CollecteLocale collecte) async {
    final pending = await getPendingCollectes();
    if (pending.any((c) => c.uuidMobile == collecte.uuidMobile)) return;
    pending.add(collecte);
    await _savePending(pending);
  }

  Future<void> _savePending(List<CollecteLocale> items) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyPending, CollecteLocale.listToJson(items));
  }

  Future<SyncResult?> getLastSyncResult() async {
    final prefs = await SharedPreferences.getInstance();
    final raw   = prefs.getString(_keyLastSync);
    if (raw == null) return null;
    try {
      return SyncResult.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      return null;
    }
  }

  Future<String> _getOrCreateDeviceId() async {
    final prefs = await SharedPreferences.getInstance();
    var id = prefs.getString(_keyDeviceId);
    if (id == null || id.isEmpty) {
      id = _generateUuid();
      await prefs.setString(_keyDeviceId, id);
    }
    return id;
  }

  // ── Synchronisation ──────────────────────────────────────────────────────────

  /// Envoie les collectes en attente au backend via POST /api/v1/sync/collectes.
  /// Retourne null si aucune collecte en attente.
  Future<SyncResult?> syncNow() async {
    final pending = await getPendingCollectes();
    if (pending.isEmpty) return null;

    final deviceId = await _getOrCreateDeviceId();
    final syncId   = _generateUuid();
    final now      = DateTime.now();
    // Formate un OffsetDateTime lisible par Java (ex: 2026-06-24T10:30:00+01:00)
    final offset  = now.timeZoneOffset;
    final sign    = offset.isNegative ? '-' : '+';
    final hh      = offset.inHours.abs().toString().padLeft(2, '0');
    final mm      = (offset.inMinutes.abs() % 60).toString().padLeft(2, '0');
    final ts      = '${now.toLocal().toIso8601String().split('.').first}$sign$hh:$mm';

    final body = {
      'syncId':              syncId,
      'deviceId':            deviceId,
      'clientSyncTimestamp': ts,
      'items': pending.map((c) {
        final item = <String, dynamic>{
          'idCollecteMobile': c.uuidMobile,
          'clientId':         c.clientIdExterne,
          'dateCollecte':     c.dateCollecte,
          'montantCollecte':  c.montantCollecte,
          'canalPaiement':    c.canalPaiement,
        };
        if (c.referenceTransaction != null) item['referenceTransaction'] = c.referenceTransaction;
        if (c.observation != null)          item['observation']          = c.observation;
        if (c.latitude != null)             item['latitude']             = c.latitude;
        if (c.longitude != null)            item['longitude']            = c.longitude;
        return item;
      }).toList(),
    };

    final data = await _api.post<Map<String, dynamic>>(
      '/api/v1/sync/collectes',
      data: body,
      fromJson: (d) => d as Map<String, dynamic>,
    );

    final result = SyncResult.fromJson(data);

    // Retire les collectes traitées (SUCCESS ou DOUBLON) du stockage local
    final resultats = (data['resultats'] as List<dynamic>?) ?? [];
    final treatedIds = resultats
        .where((r) {
          final code = (r as Map<String, dynamic>)['code']?.toString() ?? '';
          return code == 'SUCCESS' || code == 'DOUBLON';
        })
        .map((r) => (r as Map<String, dynamic>)['idCollecteMobile']?.toString() ?? '')
        .where((id) => id.isNotEmpty)
        .toSet();

    if (treatedIds.isNotEmpty) {
      final remaining = pending.where((c) => !treatedIds.contains(c.uuidMobile)).toList();
      await _savePending(remaining);
    }

    // Persiste le résumé de la dernière sync
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyLastSync, jsonEncode({
      'totalRecu':  result.totalRecu,
      'acceptees':  result.acceptees,
      'doublons':   result.doublons,
      'rejetees':   result.rejetees,
      'syncedAt':   result.syncedAt.toIso8601String(),
    }));

    return result;
  }

  static String _generateUuid() {
    final r = Random.secure();
    String seg(int len) =>
        List.generate(len, (_) => r.nextInt(16).toRadixString(16)).join();
    final v = (8 + r.nextInt(4)).toRadixString(16);
    return '${seg(8)}-${seg(4)}-4${seg(3)}-$v${seg(3)}-${seg(12)}';
  }
}
