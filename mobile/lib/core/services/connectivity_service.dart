import 'package:connectivity_plus/connectivity_plus.dart';

/// Connectivité réseau + sonde optionnelle du serveur API.
///
/// [forcedOnline] sert aux tests / simulation (contourne le plugin).
/// [healthCheck] distingue « Wi‑Fi sans serveur » d’un vrai online.
class ConnectivityService {
  ConnectivityService({this.healthCheck});

  final Connectivity _connectivity = Connectivity();
  Future<bool> Function()? healthCheck;

  /// `true` / `false` force l’état ; `null` = lecture réelle.
  bool? forcedOnline;

  bool? _reachable;
  DateTime? _lastProbe;

  Future<bool> isConnected() async {
    if (forcedOnline != null) return forcedOnline!;
    final results = await _connectivity.checkConnectivity();
    final hasLink = results.any((r) => r != ConnectivityResult.none);
    if (!hasLink) return false;
    if (healthCheck == null) return true;
    if (_reachable != null &&
        _lastProbe != null &&
        DateTime.now().difference(_lastProbe!) < const Duration(seconds: 8)) {
      return _reachable!;
    }
    _reachable = await healthCheck!();
    _lastProbe = DateTime.now();
    return _reachable!;
  }

  void invalidateProbe() {
    _reachable = null;
    _lastProbe = null;
  }

  Stream<List<ConnectivityResult>> get onConnectivityChanged =>
      _connectivity.onConnectivityChanged;
}
