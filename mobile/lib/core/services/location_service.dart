import 'dart:async';
import 'dart:convert';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api_service.dart';
import 'connectivity_service.dart';

/// Un ping GPS en attente de synchronisation (stocké offline).
class PositionLocale {
  final double latitude;
  final double longitude;
  final double? precisionMetres;
  final double? altitudeMetres;
  final double? vitesseKmh;
  final String capturedAt; // ISO-8601

  const PositionLocale({
    required this.latitude,
    required this.longitude,
    this.precisionMetres,
    this.altitudeMetres,
    this.vitesseKmh,
    required this.capturedAt,
  });

  Map<String, dynamic> toJson() => {
        'latitude': latitude,
        'longitude': longitude,
        if (precisionMetres != null) 'precisionMetres': precisionMetres,
        if (altitudeMetres != null) 'altitudeMetres': altitudeMetres,
        if (vitesseKmh != null) 'vitesseKmh': vitesseKmh,
        'capturedAt': capturedAt,
        'source': 'MOBILE',
      };

  factory PositionLocale.fromJson(Map<String, dynamic> json) => PositionLocale(
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        precisionMetres: (json['precisionMetres'] as num?)?.toDouble(),
        altitudeMetres: (json['altitudeMetres'] as num?)?.toDouble(),
        vitesseKmh: (json['vitesseKmh'] as num?)?.toDouble(),
        capturedAt: json['capturedAt'] as String,
      );
}

/// État courant du service GPS.
enum GpsState { idle, active, offline, error }

/// Service de géolocalisation continu pour les agents terrain.
///
/// Règles FINANCE SARL (gps_obligatoire = true) :
///  - Le partage GPS ne peut pas être désactivé par l'agent.
///  - En mode offline, les positions sont stockées localement et envoyées
///    dès le retour de la connexion.
///  - Le tracking continue même hors-ligne.
class LocationService extends ChangeNotifier {
  static const _queueKey      = 'gps_position_queue';
  static const _pingInterval  = Duration(minutes: 5);
  static const _distanceFilter = 30.0; // mètres

  final ApiService          _api;
  final ConnectivityService _connectivity;

  LocationService(this._api, this._connectivity);

  // ── État public ──────────────────────────────────────────────────────────────

  GpsState  _state          = GpsState.idle;
  Position? _lastPosition;
  bool      _gpsObligatoire = false;
  int       _pendingCount   = 0;
  String?   _errorMessage;

  GpsState  get state          => _state;
  Position? get lastPosition   => _lastPosition;
  bool      get gpsObligatoire => _gpsObligatoire;
  int       get pendingCount   => _pendingCount;
  String?   get errorMessage   => _errorMessage;
  bool      get isTracking     => _state == GpsState.active || _state == GpsState.offline;

  // ── Internals ────────────────────────────────────────────────────────────────

  StreamSubscription<Position>?              _positionSub;
  StreamSubscription<List<ConnectivityResult>>? _connectSub;
  Timer?                                     _pingTimer;

  // ── Démarrage du tracking ─────────────────────────────────────────────────────

  Future<void> startTracking({bool gpsObligatoire = false}) async {
    if (isTracking) return;

    _gpsObligatoire = gpsObligatoire;

    // 1) Vérifier que le service GPS est activé sur l'appareil
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      _setError('Le GPS de l\'appareil est désactivé. '
          'Activez-le dans les paramètres système.');
      return;
    }

    // 2) Demander / vérifier les permissions
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    if (permission == LocationPermission.denied ||
        permission == LocationPermission.deniedForever) {
      _setError(permission == LocationPermission.deniedForever
          ? 'Permission GPS refusée définitivement. '
              'Activez-la dans les paramètres de l\'application.'
          : 'Permission GPS refusée. La localisation est requise.');
      return;
    }

    // 3) Lire le nombre de positions en attente depuis le stockage
    await _loadPendingCount();

    // 4) Écouter le stream de positions (déclenchement à chaque mouvement ≥ 30 m)
    final settings = LocationSettings(
      accuracy: LocationAccuracy.high,
      distanceFilter: _distanceFilter.toInt(),
    );

    _positionSub = Geolocator.getPositionStream(locationSettings: settings)
        .listen(_onPositionReceived, onError: _onStreamError);

    // 5) Ping périodique (position même sans déplacement)
    _pingTimer =
        Timer.periodic(_pingInterval, (_) => _pingCurrentPosition());

    // 6) Écouter la connectivité : vider la queue au retour en ligne
    _connectSub = _connectivity.onConnectivityChanged.listen((results) {
      final online = results.any((r) => r != ConnectivityResult.none);
      if (online && _pendingCount > 0) {
        _flushQueue();
      }
      _updateState();
    });

    _state = GpsState.active;
    _errorMessage = null;
    notifyListeners();

    // 7) Envoi immédiat de la position courante
    _pingCurrentPosition();
  }

  // ── Arrêt du tracking ─────────────────────────────────────────────────────────

  /// Arrêter le tracking.
  /// Ignoré si [gpsObligatoire] est true (FINANCE SARL).
  Future<bool> stopTracking() async {
    if (_gpsObligatoire) {
      // Essayer via l'API pour avoir le message officiel du backend
      try {
        await _api.delete<void>('/api/v1/agents/me/position');
      } on ApiException catch (e) {
        if (e.statusCode == 403) {
          _errorMessage = e.message;
          notifyListeners();
          return false; // impossible de désactiver
        }
      }
      return false;
    }

    await _api
        .delete<void>('/api/v1/agents/me/position')
        .catchError((_) {});

    _cleanup();
    _state = GpsState.idle;
    notifyListeners();
    return true;
  }

  /// Arrêt propre lors de la déconnexion (sans appel API — la session expire).
  void shutdownForLogout() {
    _cleanup();
    _state = GpsState.idle;
    _gpsObligatoire = false;
    notifyListeners();
  }

  @override
  void dispose() {
    _cleanup();
    super.dispose();
  }

  void _cleanup() {
    _positionSub?.cancel();
    _connectSub?.cancel();
    _pingTimer?.cancel();
    _positionSub = null;
    _connectSub  = null;
    _pingTimer   = null;
  }

  // ── Réception d'une position ──────────────────────────────────────────────────

  void _onPositionReceived(Position pos) {
    _lastPosition = pos;
    _sendPosition(_positionFrom(pos));
  }

  void _onStreamError(Object err) {
    debugPrint('[GPS] Erreur stream : $err');
    // On ne coupe pas le tracking — le stream peut se rétablir
  }

  Future<void> _pingCurrentPosition() async {
    try {
      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
            accuracy: LocationAccuracy.high),
      );
      _lastPosition = pos;
      await _sendPosition(_positionFrom(pos));
    } catch (e) {
      debugPrint('[GPS] Ping échoué : $e');
    }
  }

  PositionLocale _positionFrom(Position pos) {
    final now = DateTime.now();
    final offset = now.timeZoneOffset;
    final sign = offset.isNegative ? '-' : '+';
    final hh = offset.inHours.abs().toString().padLeft(2, '0');
    final mm = (offset.inMinutes.abs() % 60).toString().padLeft(2, '0');
    final ts =
        '${now.toLocal().toIso8601String().split('.').first}$sign$hh:$mm';

    return PositionLocale(
      latitude: pos.latitude,
      longitude: pos.longitude,
      precisionMetres: pos.accuracy > 0 ? pos.accuracy : null,
      altitudeMetres: pos.altitude != 0 ? pos.altitude : null,
      vitesseKmh: pos.speed >= 0 ? pos.speed * 3.6 : null,
      capturedAt: ts,
    );
  }

  // ── Envoi vers le backend ─────────────────────────────────────────────────────

  Future<void> _sendPosition(PositionLocale pos) async {
    final connected = await _connectivity.isConnected();
    if (!connected) {
      await _enqueue(pos);
      _state = GpsState.offline;
      notifyListeners();
      return;
    }

    try {
      await _api.put<void>(
        '/api/v1/agents/me/position',
        data: pos.toJson(),
      );
      // Succès → vider la queue si elle contenait des éléments
      if (_pendingCount > 0) await _flushQueue();
      _state = GpsState.active;
      notifyListeners();
    } on ApiException catch (e) {
      debugPrint('[GPS] Envoi échoué (${e.statusCode}): ${e.message}');
      await _enqueue(pos);
    }
  }

  // ── Queue offline (SharedPreferences) ────────────────────────────────────────

  Future<void> _enqueue(PositionLocale pos) async {
    final prefs = await SharedPreferences.getInstance();
    final raw   = prefs.getString(_queueKey) ?? '[]';
    final list  = (jsonDecode(raw) as List<dynamic>).cast<Map<String, dynamic>>();
    // Limiter la queue à 500 positions pour éviter la saturation
    if (list.length >= 500) list.removeAt(0);
    list.add(pos.toJson());
    await prefs.setString(_queueKey, jsonEncode(list));
    _pendingCount = list.length;
    notifyListeners();
  }

  Future<void> _flushQueue() async {
    final prefs = await SharedPreferences.getInstance();
    final raw   = prefs.getString(_queueKey);
    if (raw == null || raw == '[]') {
      _pendingCount = 0;
      notifyListeners();
      return;
    }

    List<Map<String, dynamic>> queue;
    try {
      queue = (jsonDecode(raw) as List<dynamic>).cast<Map<String, dynamic>>();
    } catch (_) {
      await prefs.remove(_queueKey);
      _pendingCount = 0;
      notifyListeners();
      return;
    }

    final failed = <Map<String, dynamic>>[];
    for (final item in queue) {
      try {
        await _api.put<void>('/api/v1/agents/me/position', data: item);
      } catch (_) {
        failed.add(item);
      }
    }

    if (failed.isEmpty) {
      await prefs.remove(_queueKey);
    } else {
      await prefs.setString(_queueKey, jsonEncode(failed));
    }
    _pendingCount = failed.length;
    _state = failed.isEmpty ? GpsState.active : GpsState.offline;
    notifyListeners();
    debugPrint('[GPS] Queue vidée: ${queue.length - failed.length} envoyées, ${failed.length} en attente');
  }

  Future<void> _loadPendingCount() async {
    final prefs = await SharedPreferences.getInstance();
    final raw   = prefs.getString(_queueKey);
    if (raw == null || raw == '[]') {
      _pendingCount = 0;
      return;
    }
    try {
      _pendingCount = (jsonDecode(raw) as List<dynamic>).length;
    } catch (_) {
      _pendingCount = 0;
    }
  }

  void _updateState() {
    if (!isTracking) return;
    _connectivity.isConnected().then((online) {
      final next = online ? GpsState.active : GpsState.offline;
      if (_state != next) {
        _state = next;
        notifyListeners();
      }
    });
  }

  void _setError(String msg) {
    _state = GpsState.error;
    _errorMessage = msg;
    notifyListeners();
  }
}
