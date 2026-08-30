import '../models/client.dart';
import '../utils/api_unwrap.dart';
import 'api_service.dart';
import 'connectivity_service.dart';
import 'local_database.dart';

class ClientService {
  ClientService(this._api, this._db, this._connectivity);

  final ApiService _api;
  final LocalDatabase _db;
  final ConnectivityService _connectivity;

  /// Recherche : met à jour le cache si en ligne, lit toujours SQLite.
  Future<List<Client>> searchClients({String? search}) async {
    if (await _connectivity.isConnected()) {
      try {
        final remote = await _fetchRemote(search: search);
        if (remote.isNotEmpty) await _db.upsertClients(remote);
      } catch (_) {
        // Fallback cache local
      }
    }
    final limit = (search == null || search.trim().isEmpty) ? 300 : 80;
    return _db.searchClients(search, limit: limit);
  }

  Future<Client> getClientDetail(String idClient) async {
    if (await _connectivity.isConnected()) {
      try {
        final client = await _fetchById(idClient);
        await _db.upsertClients([client]);
        return client;
      } catch (_) {}
    }
    final local = await _db.getClient(idClient);
    if (local != null) return local;
    throw ApiException('Client introuvable hors ligne', statusCode: 404);
  }

  /// Télécharge le portefeuille page par page (appelé après login / reconnexion).
  Future<int> prefetchAll({int pageSize = 100}) async {
    if (!await _connectivity.isConnected()) return _db.clientCount();
    var page = 0;
    var last = false;
    var total = 0;
    while (!last && page < 50) {
      final chunk = await _fetchPage(page: page, size: pageSize);
      if (chunk.clients.isNotEmpty) {
        await _db.upsertClients(chunk.clients);
        total += chunk.clients.length;
      }
      last = chunk.last || chunk.clients.isEmpty;
      page++;
    }
    return total;
  }

  Future<int> cachedCount() => _db.clientCount();

  Future<List<Client>> _fetchRemote({String? search}) async {
    if (search != null && search.trim().isNotEmpty) {
      return _api.get<List<Client>>(
        '/api/v1/clients/search',
        queryParameters: {'q': search.trim(), 'limit': 40},
        fromJson: (data) => unwrapApiList(data)
            .map((e) => Client.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
    }
    return (await _fetchPage(page: 0, size: 80)).clients;
  }

  Future<({List<Client> clients, bool last})> _fetchPage({
    required int page,
    required int size,
  }) async {
    return _api.get<({List<Client> clients, bool last})>(
      '/api/v1/clients',
      queryParameters: {'page': page, 'size': size},
      fromJson: (data) {
        final payload = unwrapApiMap(data);
        final content = payload['content'] as List<dynamic>? ?? [];
        final last = payload['last'] as bool? ?? content.length < size;
        return (
          clients: content
              .map((e) => Client.fromJson(e as Map<String, dynamic>))
              .toList(),
          last: last,
        );
      },
    );
  }

  Future<Client> _fetchById(String id) {
    return _api.get<Client>(
      '/api/v1/clients/$id',
      fromJson: (data) => Client.fromJson(unwrapApiMap(data)),
    );
  }
}
