import 'dart:convert';
import 'dart:io';

/// Mini-serveur qui parle le contrat Spring `/api/v1` (health, clients, sync, GPS).
class FakeBackend {
  FakeBackend({required this.name});

  final String name;
  HttpServer? _server;
  int port = 0;

  final List<Map<String, dynamic>> clients = [];
  final List<Map<String, dynamic>> collectes = [];
  final List<Map<String, dynamic>> positions = [];
  int dashboardCollecteJour = 0;

  String get baseUrl => 'http://127.0.0.1:$port';

  Future<void> start() async {
    _server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    port = _server!.port;
    _server!.listen(_handle);
  }

  Future<void> stop() async {
    await _server?.close(force: true);
  }

  Future<void> _handle(HttpRequest req) async {
    final path = req.uri.path;
    req.response.headers.contentType = ContentType.json;

    if (req.method == 'GET' && path.endsWith('/api/v1/health')) {
      _write(req, {'status': 'EN_LIGNE', 'service': name});
      return;
    }

    if (req.method == 'GET' && path.endsWith('/api/v1/clients/search')) {
      final q = (req.uri.queryParameters['q'] ?? '').toLowerCase();
      final hits = clients.where((c) {
        final blob = '${c['nom']} ${c['prenom']} ${c['telephone']} ${c['idClient']}'.toLowerCase();
        return q.isEmpty || blob.contains(q);
      }).toList();
      _write(req, {'success': true, 'data': hits});
      return;
    }

    if (req.method == 'GET' && path.contains('/api/v1/clients/')) {
      final id = path.split('/').last;
      if (id.isNotEmpty && id != 'clients') {
        final found = clients.where((c) => '${c['idClient']}' == id);
        if (found.isEmpty) {
          req.response.statusCode = 404;
          _write(req, {'success': false, 'message': 'introuvable'});
          return;
        }
        _write(req, {'success': true, 'data': found.first});
        return;
      }
    }

    if (req.method == 'GET' && path.endsWith('/api/v1/clients')) {
      final page = int.tryParse(req.uri.queryParameters['page'] ?? '0') ?? 0;
      final size = int.tryParse(req.uri.queryParameters['size'] ?? '80') ?? 80;
      final start = page * size;
      final slice = start >= clients.length
          ? <Map<String, dynamic>>[]
          : clients.sublist(start, (start + size).clamp(0, clients.length));
      _write(req, {
        'success': true,
        'data': {
          'content': slice,
          'last': start + slice.length >= clients.length,
          'totalElements': clients.length,
        },
      });
      return;
    }

    if (req.method == 'GET' && path.endsWith('/api/v1/agent/dashboard')) {
      _write(req, {
        'success': true,
        'data': {
          'objectifJour': 50000,
          'collecteJour': dashboardCollecteJour,
          'collectesCount': collectes.length,
          'clientsVisites': collectes.map((c) => c['clientId']).toSet().length,
          'clientsTotal': clients.length,
          'synchronise': true,
          'alertesClients': [],
        },
      });
      return;
    }

    if (req.method == 'GET' && path.endsWith('/api/v1/alertes')) {
      _write(req, {
        'content': [],
        'totalElements': 0,
        'totalPages': 0,
        'number': 0,
        'size': 20,
        'first': true,
        'last': true,
      });
      return;
    }

    if (req.method == 'POST' && path.endsWith('/api/v1/sync/collectes')) {
      final body = jsonDecode(await utf8.decoder.bind(req).join()) as Map<String, dynamic>;
      final items = (body['items'] as List<dynamic>? ?? []).cast<Map<String, dynamic>>();
      final resultats = <Map<String, dynamic>>[];
      var succes = 0;
      var doublons = 0;
      var conflits = 0;
      var erreurs = 0;
      for (final item in items) {
        final id = item['idCollecteMobile']?.toString() ?? '';
        final montant = (item['montantCollecte'] as num?)?.toDouble() ?? 0;
        if (montant <= 0) {
          conflits++;
          resultats.add({
            'idCollecteMobile': id,
            'code': 'CONFLIT',
            'message': 'Montant invalide',
          });
          continue;
        }
        final exists = collectes.any((c) => c['idCollecteMobile'] == id);
        if (exists) {
          doublons++;
          resultats.add({
            'idCollecteMobile': id,
            'code': 'DOUBLON',
            'message': 'Déjà enregistrée',
          });
          continue;
        }
        collectes.add(item);
        dashboardCollecteJour += montant.toInt();
        succes++;
        resultats.add({
          'idCollecteMobile': id,
          'code': 'SUCCESS',
          'message': 'Collecte confirmée',
        });
      }
      _write(req, {
        'success': true,
        'message': '$succes acceptées, $doublons doublons',
        'data': {
          'syncId': body['syncId'],
          'statutGlobal': 'COMPLETE',
          'stats': {
            'total': items.length,
            'succes': succes,
            'doublons': doublons,
            'conflits': conflits,
            'erreurs': erreurs,
          },
          'resultats': resultats,
        },
      });
      return;
    }

    if (req.method == 'PUT' && path.endsWith('/api/v1/agents/me/position')) {
      final body = jsonDecode(await utf8.decoder.bind(req).join()) as Map<String, dynamic>;
      positions.add(body);
      _write(req, {'success': true});
      return;
    }

    req.response.statusCode = 404;
    _write(req, {'success': false, 'message': 'not found $path'});
  }

  void _write(HttpRequest req, Object body) {
    req.response.write(jsonEncode(body));
    req.response.close();
  }
}
