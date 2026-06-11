import '../models/alerte.dart';
import '../models/page_response.dart';
import 'api_service.dart';

class AlerteService {
  final ApiService _api;

  AlerteService(this._api);

  Future<PageResponse<Alerte>> getAlertes({
    String? statut,
    int page = 0,
    int size = 20,
  }) async {
    final params = <String, dynamic>{
      'page': page,
      'size': size,
    };
    if (statut != null && statut.isNotEmpty) params['statut'] = statut;

    return _api.get<PageResponse<Alerte>>(
      '/api/v1/alertes',
      queryParameters: params,
      fromJson: (data) => PageResponse.fromJson(
        data as Map<String, dynamic>,
        (json) => Alerte.fromJson(json),
      ),
    );
  }

  Future<Alerte> getAlerteDetail(int id) async {
    return _api.get<Alerte>(
      '/api/v1/alertes/$id',
      fromJson: (data) => Alerte.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<Alerte> updateStatut(int id, String statut) async {
    return _api.put<Alerte>(
      '/api/v1/alertes/$id/statut',
      data: {'statut': statut},
      fromJson: (data) => Alerte.fromJson(data as Map<String, dynamic>),
    );
  }
}
