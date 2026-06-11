import '../models/kpi_summary.dart';
import 'api_service.dart';

class KpiService {
  final ApiService _api;

  KpiService(this._api);

  Future<KpiSummary> getSummary({
    String? dateDebut,
    String? dateFin,
  }) async {
    final params = <String, dynamic>{};
    if (dateDebut != null) params['dateDebut'] = dateDebut;
    if (dateFin != null) params['dateFin'] = dateFin;

    return _api.get<KpiSummary>(
      '/api/v1/kpi/summary',
      queryParameters: params.isEmpty ? null : params,
      fromJson: (data) => KpiSummary.fromJson(data as Map<String, dynamic>),
    );
  }
}
