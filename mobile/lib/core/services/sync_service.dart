import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/collecte_locale.dart';
import 'api_service.dart';

/// Service de synchronisation des collectes offline-first.
///
/// Stockage local : liste JSON dans SharedPreferences (clé _keyPending).
/// Sync : POST /api/v1/collectes-epargne/sync — le backend déduplique par UUID.
/// Après sync réussie : les collectes acceptées déclenchent le scoring MCRS
/// temps réel côté serveur (SyncEventListener → RealtimeScoringService).
class SyncService {
  static const _keyPending   = 'collectes_pending';
  static const _keyLastSync  = 'last_sync_result';

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
    // Déduplication UUID côté mobile
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

  // ── Synchronisation ──────────────────────────────────────────────────────────

  /// Envoie les collectes en attente au backend.
  /// Retourne null si aucune collecte en attente.
  /// Après un succès partiel ou total, retire les collectes acceptées du stockage local.
  Future<SyncResult?> syncNow() async {
    final pending = await getPendingCollectes();
    if (pending.isEmpty) return null;

    final body = {
      'collectes': pending.map((c) => c.toJson()).toList(),
    };

    final data = await _api.post<Map<String, dynamic>>(
      '/api/v1/collectes-epargne/sync',
      data: body,
      fromJson: (d) => d as Map<String, dynamic>,
    );

    final result = SyncResult.fromJson(data);

    // Retire les collectes acceptées (et doublons) du stockage local
    final acceptedUuids = (data['uuidsAcceptes'] as List<dynamic>?)
            ?.map((e) => e.toString())
            .toSet() ??
        {};
    final doubonsUuids = (data['uuidsDoublons'] as List<dynamic>?)
            ?.map((e) => e.toString())
            .toSet() ??
        {};
    final treated = acceptedUuids.union(doubonsUuids);

    if (treated.isNotEmpty) {
      final remaining =
          pending.where((c) => !treated.contains(c.uuidMobile)).toList();
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
}
