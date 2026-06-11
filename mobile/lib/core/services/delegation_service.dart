import '../models/delegation.dart';
import '../models/page_response.dart';
import 'api_service.dart';

class DelegationService {
  final ApiService _api;

  DelegationService(this._api);

  Future<List<Delegation>> mesDelegations() async {
    return _api.get<List<Delegation>>(
      '/api/v1/delegations/mes-delegations',
      fromJson: (data) {
        final list = data as List<dynamic>;
        return list
            .map((e) => Delegation.fromJson(e as Map<String, dynamic>))
            .toList();
      },
    );
  }

  Future<PageResponse<Delegation>> listDelegations({
    int page = 0,
    int size = 20,
  }) async {
    return _api.get<PageResponse<Delegation>>(
      '/api/v1/delegations',
      queryParameters: {'page': page, 'size': size},
      fromJson: (data) => PageResponse.fromJson(
        data as Map<String, dynamic>,
        (json) => Delegation.fromJson(json),
      ),
    );
  }

  Future<List<AgentCreditItem>> getAgentsCredit() async {
    return _api.get<List<AgentCreditItem>>(
      '/api/v1/delegations/agents-credit',
      fromJson: (data) {
        final list = data as List<dynamic>;
        return list
            .map((e) => AgentCreditItem.fromJson(e as Map<String, dynamic>))
            .toList();
      },
    );
  }

  Future<Delegation> reassignerDossier({
    required String dossierUid,
    required String nouvelAgentUid,
    String? motif,
  }) async {
    return _api.patch<Delegation>(
      '/api/v1/dossiers-credit/$dossierUid/reassigner',
      data: {'nouvelAgentUid': nouvelAgentUid, if (motif != null) 'motif': motif},
      fromJson: (data) => Delegation.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<void> revoquer(String delegationUid) async {
    await _api.delete('/api/v1/delegations/$delegationUid/revoquer');
  }
}
