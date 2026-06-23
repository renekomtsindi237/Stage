import '../models/client.dart';
import 'api_service.dart';

class ClientService {
  final ApiService _api;

  ClientService(this._api);

  Future<List<Client>> searchClients({String? search}) async {
    final params = <String, dynamic>{};
    if (search != null && search.isNotEmpty) params['search'] = search;

    return _api.get<List<Client>>(
      '/api/v1/clients',
      queryParameters: params.isEmpty ? null : params,
      fromJson: (data) {
        final map = data as Map<String, dynamic>;
        // Unwrap ApiResponse wrapper if present
        final payload = map.containsKey('success')
            ? map['data'] as Map<String, dynamic>
            : map;
        // Extract PageResponse.content
        final content = payload['content'] as List<dynamic>? ?? [];
        return content
            .map((e) => Client.fromJson(e as Map<String, dynamic>))
            .toList();
      },
    );
  }

  Future<Client> getClientDetail(int idClient) async {
    return _api.get<Client>(
      '/api/v1/clients/$idClient',
      fromJson: (data) {
        final map = data as Map<String, dynamic>;
        final payload = map.containsKey('success')
            ? map['data'] as Map<String, dynamic>
            : map;
        return Client.fromJson(payload);
      },
    );
  }
}
