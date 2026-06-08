import 'dart:async';
import 'package:flutter/foundation.dart';
import '../models/collecte_locale.dart';
import '../services/sync_service.dart';
import '../services/sse_service.dart';
import '../services/connectivity_service.dart';

enum ScoringState { idle, pending, done, unavailable }

/// Provider de synchronisation et scoring temps réel.
///
/// Cycle complet :
///  1. Agent saisit des collectes hors ligne → [ajouterCollecte]
///  2. Retour connectivité → auto-sync déclenchée
///  3. Sync réussie → backend déclenche scoring MCRS (async)
///  4. Backend pousse SSE SCORING_UPDATE → [scoringState] passe à [done]
///  5. Dashboard rafraîchi avec le badge de scoring
class SyncProvider extends ChangeNotifier {
  final SyncService         _syncService;
  final SseService          _sseService;
  final ConnectivityService _connectivityService;

  SyncProvider(this._syncService, this._sseService, this._connectivityService) {
    _init();
  }

  // ── État public ──────────────────────────────────────────────────────────────

  int           pendingCount  = 0;
  SyncResult?   lastResult;
  bool          syncing       = false;
  String?       syncError;
  ScoringState  scoringState  = ScoringState.idle;
  List<dynamic> latestScores  = [];

  // ── Init ─────────────────────────────────────────────────────────────────────

  void _init() {
    _loadPendingCount();
    _listenConnectivity();
    _listenSse();
    _sseService.connect();
  }

  Future<void> _loadPendingCount() async {
    final items = await _syncService.getPendingCollectes();
    pendingCount = items.length;
    final last   = await _syncService.getLastSyncResult();
    lastResult   = last;
    notifyListeners();
  }

  void _listenConnectivity() {
    _connectivityService.onConnectivityChanged.listen((results) async {
      final connected = results.any((r) => r.name != 'none');
      if (connected && pendingCount > 0) {
        // Reconnexion avec des collectes en attente → auto-sync
        await syncNow();
        _sseService.connect();
      }
    });
  }

  void _listenSse() {
    _sseService.events.listen((event) {
      if (event.type == 'SCORING_UPDATE') {
        scoringState = ScoringState.done;
        if (event.payload is List) {
          latestScores = event.payload as List<dynamic>;
        }
        notifyListeners();
      }
    });
  }

  // ── Actions publiques ────────────────────────────────────────────────────────

  Future<void> ajouterCollecte(CollecteLocale collecte) async {
    await _syncService.ajouterCollecteLocale(collecte);
    pendingCount++;
    notifyListeners();
  }

  /// Lance la synchronisation manuelle ou automatique.
  Future<SyncResult?> syncNow() async {
    if (syncing) return null;
    syncing      = true;
    syncError    = null;
    scoringState = ScoringState.idle;
    notifyListeners();

    try {
      final result = await _syncService.syncNow();
      if (result == null) {
        syncing = false;
        notifyListeners();
        return null;
      }

      lastResult   = result;
      pendingCount = (await _syncService.getPendingCollectes()).length;

      if (result.acceptees > 0) {
        // Scoring en cours côté serveur — on attend le SSE
        scoringState = ScoringState.pending;
        // Fallback si le SSE ne répond pas dans les 15s
        _startScoringTimeout();
      }

      return result;
    } catch (e) {
      syncError = e.toString().replaceFirst('ApiException(', '').replaceFirst(')', '');
    } finally {
      syncing = false;
      notifyListeners();
    }
    return null;
  }

  void _startScoringTimeout() {
    Future.delayed(const Duration(seconds: 15), () {
      if (scoringState == ScoringState.pending) {
        scoringState = ScoringState.unavailable;
        notifyListeners();
      }
    });
  }

  void resetScoringBadge() {
    scoringState = ScoringState.idle;
    latestScores = [];
    notifyListeners();
  }

  @override
  void dispose() {
    _sseService.dispose();
    super.dispose();
  }
}
