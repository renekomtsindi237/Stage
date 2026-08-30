import 'package:flutter/foundation.dart';

import 'agent_service.dart';
import 'alerte_service.dart';
import 'client_service.dart';
import 'connectivity_service.dart';

/// Remplit SQLite (clients, dashboard, alertes) dès qu'un réseau est disponible.
class OfflineCatalogService {
  OfflineCatalogService({
    required ClientService clients,
    required AgentService agent,
    required AlerteService alertes,
    required ConnectivityService connectivity,
  })  : _clients = clients,
        _agent = agent,
        _alertes = alertes,
        _connectivity = connectivity;

  final ClientService _clients;
  final AgentService _agent;
  final AlerteService _alertes;
  final ConnectivityService _connectivity;

  bool _running = false;

  Future<void> refresh() async {
    if (_running) return;
    if (!await _connectivity.isConnected()) return;
    _running = true;
    try {
      await _clients.prefetchAll();
      await _agent.getAgentDashboard();
      await _alertes.getAlertes(page: 0, size: 20);
    } catch (e) {
      debugPrint('[offline] prefetch: $e');
    } finally {
      _running = false;
    }
  }
}
