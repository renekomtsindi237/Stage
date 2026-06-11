import '../models/recouvrement.dart';
import 'api_service.dart';

class PagedRecouvrement {
  final List<DossierRecouvrement> content;
  final int totalElements;
  const PagedRecouvrement({required this.content, required this.totalElements});
}

class RecouvrementService {
  final ApiService _api;
  RecouvrementService(this._api);

  Future<PagedRecouvrement> listDossiers({
    String? phase,
    bool? clos,
    int page = 0,
    int size = 20,
  }) async {
    final params = <String, dynamic>{'page': page, 'size': size};
    if (phase != null) params['phase'] = phase;
    if (clos != null) params['clos'] = clos.toString();

    final resp = await _api.get<Map<String, dynamic>>(
      '/api/v1/recouvrement/dossiers',
      queryParameters: params,
      fromJson: (d) => d as Map<String, dynamic>,
    );
    final data = resp['data'] as Map<String, dynamic>?;
    final items = (data?['content'] as List<dynamic>? ?? [])
        .map((e) => DossierRecouvrement.fromJson(e as Map<String, dynamic>))
        .toList();
    return PagedRecouvrement(
      content: items,
      totalElements: (data?['totalElements'] as num?)?.toInt() ?? 0,
    );
  }

  Future<DossierRecouvrement> getDossier(int id) async {
    final resp = await _api.get<Map<String, dynamic>>(
      '/api/v1/recouvrement/dossiers/$id',
      fromJson: (d) => d as Map<String, dynamic>,
    );
    return DossierRecouvrement.fromJson(resp['data'] as Map<String, dynamic>);
  }

  Future<List<ActionRecouvrement>> getActions(int dossierId) async {
    final resp = await _api.get<Map<String, dynamic>>(
      '/api/v1/recouvrement/dossiers/$dossierId/actions',
      fromJson: (d) => d as Map<String, dynamic>,
    );
    final list = resp['data'] as List<dynamic>? ?? [];
    return list.map((e) => ActionRecouvrement.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<ActionRecouvrement> ajouterAction(int dossierId, Map<String, dynamic> body) async {
    final resp = await _api.post<Map<String, dynamic>>(
      '/api/v1/recouvrement/dossiers/$dossierId/actions',
      data: body,
      fromJson: (d) => d as Map<String, dynamic>,
    );
    return ActionRecouvrement.fromJson(resp['data'] as Map<String, dynamic>);
  }

  Future<DossierRecouvrement> escalader(int id, String nouvellePhase, {String? motif}) async {
    final resp = await _api.put<Map<String, dynamic>>(
      '/api/v1/recouvrement/dossiers/$id/escalader',
      data: {'nouvellePhase': nouvellePhase, if (motif != null) 'motif': motif},
      fromJson: (d) => d as Map<String, dynamic>,
    );
    return DossierRecouvrement.fromJson(resp['data'] as Map<String, dynamic>);
  }
}
