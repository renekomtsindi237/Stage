import 'dart:convert';

import '../models/alerte.dart';
import '../models/page_response.dart';
import '../utils/api_unwrap.dart';
import 'api_service.dart';
import 'connectivity_service.dart';
import 'local_database.dart';

class AlerteService {
  AlerteService(this._api, this._db, this._connectivity);

  final ApiService _api;
  final LocalDatabase _db;
  final ConnectivityService _connectivity;

  Future<PageResponse<Alerte>> getAlertes({
    String? statut,
    int page = 0,
    int size = 20,
  }) async {
    if (await _connectivity.isConnected()) {
      try {
        final remote = await _fetchRemote(statut: statut, page: page, size: size);
        if (page == 0 && (statut == null || statut.isEmpty)) {
          await _db.putKv(
            'alertes',
            jsonEncode(remote.content.map((e) => e.toJson()).toList()),
          );
        }
        return remote;
      } catch (_) {}
    }
    if (page == 0) {
      final raw = await _db.getKv('alertes');
      if (raw != null) {
        final list = (jsonDecode(raw) as List<dynamic>)
            .map((e) => Alerte.fromJson(e as Map<String, dynamic>))
            .where((a) => statut == null || statut.isEmpty || a.statut == statut)
            .toList();
        return PageResponse(
          content: list,
          totalElements: list.length,
          totalPages: 1,
          number: 0,
          size: list.length,
          first: true,
          last: true,
        );
      }
    }
    throw ApiException(
      'Alertes indisponibles hors ligne.',
      statusCode: 503,
    );
  }

  Future<PageResponse<Alerte>> _fetchRemote({
    String? statut,
    int page = 0,
    int size = 20,
  }) {
    final params = <String, dynamic>{'page': page, 'size': size};
    if (statut != null && statut.isNotEmpty) params['statut'] = statut;
    return _api.get<PageResponse<Alerte>>(
      '/api/v1/alertes',
      queryParameters: params,
      fromJson: (data) {
        final map = unwrapApiMap(data);
        return PageResponse.fromJson(map, Alerte.fromJson);
      },
    );
  }

  Future<Alerte> getAlerteDetail(int id) async {
    return _api.get<Alerte>(
      '/api/v1/alertes/$id',
      fromJson: (data) => Alerte.fromJson(unwrapApiMap(data)),
    );
  }

  Future<Alerte> updateStatut(int id, String statut) async {
    return _api.put<Alerte>(
      '/api/v1/alertes/$id/statut',
      data: {'statut': statut},
      fromJson: (data) => Alerte.fromJson(unwrapApiMap(data)),
    );
  }
}
