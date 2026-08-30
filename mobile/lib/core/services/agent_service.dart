import '../models/agent_dashboard_data.dart';
import '../utils/api_unwrap.dart';
import 'api_service.dart';
import 'connectivity_service.dart';
import 'local_database.dart';

class AgentService {
  AgentService(this._api, this._db, this._connectivity);

  final ApiService _api;
  final LocalDatabase _db;
  final ConnectivityService _connectivity;

  bool lastFromCache = false;
  DateTime? lastCachedAt;

  Future<AgentDashboardData> getAgentDashboard() async {
    if (await _connectivity.isConnected()) {
      try {
        final data = await _fetchRemote();
        await _db.putJson('dashboard', data.toJson());
        lastFromCache = false;
        lastCachedAt = DateTime.now();
        return data;
      } catch (_) {}
    }
    final cached = await _db.getJson('dashboard');
    if (cached != null) {
      lastFromCache = true;
      lastCachedAt = await _db.getKvTime('dashboard');
      return AgentDashboardData.fromJson(cached);
    }
    throw ApiException(
      'Tableau de bord indisponible hors ligne. Connectez-vous une fois pour le mettre en cache.',
      statusCode: 503,
    );
  }

  Future<AgentDashboardData> _fetchRemote() {
    return _api.get<AgentDashboardData>(
      '/api/v1/agent/dashboard',
      fromJson: (data) => AgentDashboardData.fromJson(unwrapApiMap(data)),
    );
  }

  Future<void> updatePosition({
    required double latitude,
    required double longitude,
    double? precisionMetres,
    double? altitudeMetres,
    double? vitesseKmh,
    double? capDegres,
    String? source,
    String? collecteUuid,
  }) async {
    final payload = {
      'latitude': latitude,
      'longitude': longitude,
      if (precisionMetres != null) 'precisionMetres': precisionMetres,
      if (altitudeMetres != null) 'altitudeMetres': altitudeMetres,
      if (vitesseKmh != null) 'vitesseKmh': vitesseKmh,
      if (capDegres != null) 'capDegres': capDegres,
      'source': source ?? 'MOBILE',
      if (collecteUuid != null) 'collecteUuid': collecteUuid,
    };
    if (!await _connectivity.isConnected()) {
      await _db.enqueueGps(payload);
      return;
    }
    try {
      await _api.put('/api/v1/agents/me/position', data: payload);
    } catch (_) {
      await _db.enqueueGps(payload);
    }
  }

  Future<void> disablePosition() async {
    if (!await _connectivity.isConnected()) return;
    await _api.delete('/api/v1/agents/me/position');
  }
}
