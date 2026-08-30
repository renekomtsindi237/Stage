import '../models/collecte_locale.dart';
import '../utils/uuid_v4.dart';
import 'api_service.dart';
import 'local_database.dart';

class SyncService {
  SyncService(this._api, this._db);

  final ApiService _api;
  final LocalDatabase _db;

  Future<List<CollecteLocale>> getPendingCollectes() => _db.pendingCollectes();

  Future<List<CollecteLocale>> getSyncedCollectes() => _db.syncedCollectes();

  Future<void> ajouterCollecteLocale(CollecteLocale collecte) async {
    await _db.insertCollecte(collecte);
  }

  Future<SyncResult?> getLastSyncResult() async {
    final json = await _db.getJson('last_sync');
    if (json == null) return null;
    try {
      return SyncResult.fromJson(json);
    } catch (_) {
      return null;
    }
  }

  /// Pousse l'outbox locale vers le serveur courant, puis vide le GPS en attente.
  Future<SyncResult?> syncNow() async {
    final pending = await getPendingCollectes();
    SyncResult? result;
    if (pending.isNotEmpty) {
      result = await _pushCollectes(pending);
    }
    await flushGps();
    return result;
  }

  Future<SyncResult> _pushCollectes(List<CollecteLocale> pending) async {
    final deviceId = await _db.deviceId();
    final syncId = generateUuidV4();
    final now = DateTime.now();
    final offset = now.timeZoneOffset;
    final sign = offset.isNegative ? '-' : '+';
    final hh = offset.inHours.abs().toString().padLeft(2, '0');
    final mm = (offset.inMinutes.abs() % 60).toString().padLeft(2, '0');
    final ts =
        '${now.toLocal().toIso8601String().split('.').first}$sign$hh:$mm';

    final body = {
      'syncId': syncId,
      'deviceId': deviceId,
      'clientSyncTimestamp': ts,
      'items': pending.map((c) {
        final item = <String, dynamic>{
          'idCollecteMobile': c.uuidMobile,
          'clientId': c.clientIdExterne,
          'dateCollecte': c.dateCollecte,
          'montantCollecte': c.montantCollecte,
          'canalPaiement': c.canalPaiement,
        };
        if (c.referenceTransaction != null) {
          item['referenceTransaction'] = c.referenceTransaction;
        }
        if (c.observation != null) item['observation'] = c.observation;
        if (c.latitude != null) item['latitude'] = c.latitude;
        if (c.longitude != null) item['longitude'] = c.longitude;
        return item;
      }).toList(),
    };

    final raw = await _api.post<Map<String, dynamic>>(
      '/api/v1/sync/collectes',
      data: body,
      fromJson: (d) => d as Map<String, dynamic>,
    );
    final data = SyncResult.unwrapPayload(raw);
    final result = SyncResult.fromJson(data);

    final resultats = (data['resultats'] as List<dynamic>?) ?? [];
    final byId = <String, Map<String, dynamic>>{};
    for (final r in resultats) {
      final map = r as Map<String, dynamic>;
      final id = map['idCollecteMobile']?.toString() ?? '';
      if (id.isNotEmpty) byId[id] = map;
    }

    final treatedIds = <String>{};
    for (final c in pending) {
      final row = byId[c.uuidMobile];
      final code = row?['code']?.toString() ?? '';
      final message = row?['message']?.toString() ?? code;
      if (code == 'SUCCESS' || code == 'DOUBLON') {
        await _db.archiveCollecte(
          c: c,
          code: code,
          serverUrl: _api.baseUrl,
        );
        treatedIds.add(c.uuidMobile);
      } else if (code.isNotEmpty) {
        await _db.markCollecteError(c.uuidMobile, code, message);
      }
    }
    await _db.deleteCollectes(treatedIds);
    await _db.putJson('last_sync', {
      'totalRecu': result.totalRecu,
      'acceptees': result.acceptees,
      'doublons': result.doublons,
      'rejetees': result.rejetees,
      'syncedAt': result.syncedAt.toIso8601String(),
      'serverUrl': _api.baseUrl,
    });
    return result;
  }

  Future<int> flushGps() async {
    final queue = await _db.pendingGps();
    if (queue.isEmpty) return 0;
    final sent = <int>{};
    for (final item in queue) {
      try {
        await _api.put<void>('/api/v1/agents/me/position', data: item.payload);
        sent.add(item.id);
      } catch (_) {}
    }
    await _db.deleteGps(sent);
    return sent.length;
  }

  Future<void> switchServer(String url) async {
    _api.setBaseUrl(url);
    await _db.putKv('server_url', url);
    await _db.clearCatalogCache();
  }

  Future<String> currentServerUrl() async {
    final stored = await _db.getKv('server_url');
    if (stored != null && stored.isNotEmpty) return stored;
    return _api.baseUrl;
  }
}
