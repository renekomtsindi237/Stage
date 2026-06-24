import '../models/agent_dashboard_data.dart';
import 'api_service.dart';

class AgentService {
  final ApiService _api;

  AgentService(this._api);

  Future<AgentDashboardData> getAgentDashboard() async {
    return _api.get<AgentDashboardData>(
      '/api/v1/agent/dashboard',
      fromJson: (data) {
        final body = data as Map<String, dynamic>;
        final content = body['data'] as Map<String, dynamic>? ?? {};
        return AgentDashboardData.fromJson(content);
      },
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
    await _api.put(
      '/api/v1/agents/me/position',
      data: {
        'latitude': latitude,
        'longitude': longitude,
        if (precisionMetres != null) 'precisionMetres': precisionMetres,
        if (altitudeMetres != null) 'altitudeMetres': altitudeMetres,
        if (vitesseKmh != null) 'vitesseKmh': vitesseKmh,
        if (capDegres != null) 'capDegres': capDegres,
        'source': source ?? 'MOBILE',
        if (collecteUuid != null) 'collecteUuid': collecteUuid,
      },
    );
  }

  Future<void> disablePosition() async {
    await _api.delete('/api/v1/agents/me/position');
  }
}
