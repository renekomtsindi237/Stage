import '../models/pret.dart';
import '../models/echeance.dart';
import '../models/page_response.dart';
import 'api_service.dart';

class PretService {
  final ApiService _api;

  PretService(this._api);

  Future<PageResponse<Pret>> getPrets({
    String? statut,
    int page = 0,
    int size = 20,
  }) async {
    final params = <String, dynamic>{
      'page': page,
      'size': size,
    };
    if (statut != null && statut.isNotEmpty) params['statut'] = statut;

    return _api.get<PageResponse<Pret>>(
      '/api/v1/prets',
      queryParameters: params,
      fromJson: (data) => PageResponse.fromJson(
        data as Map<String, dynamic>,
        (json) => Pret.fromJson(json),
      ),
    );
  }

  Future<Pret> getPretDetail(int idPret) async {
    return _api.get<Pret>(
      '/api/v1/prets/$idPret',
      fromJson: (data) => Pret.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<List<Echeance>> getEcheances(int idPret) async {
    return _api.get<List<Echeance>>(
      '/api/v1/echeances/pret/$idPret',
      fromJson: (data) {
        final list = data as List<dynamic>;
        return list
            .map((e) => Echeance.fromJson(e as Map<String, dynamic>))
            .toList();
      },
    );
  }
}
