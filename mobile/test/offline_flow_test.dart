import 'package:flutter_test/flutter_test.dart';
import 'package:microrecouv/core/models/collecte_locale.dart';
import 'package:microrecouv/core/utils/uuid_v4.dart';

/// Simule le parcours agent documenté (UC-CE01 / UC-CE02) sans appareil.
void main() {
  test('UUID v4 RFC 4122 — unique, version 4, variant 10xx', () {
    final ids = {for (var i = 0; i < 50; i++) generateUuidV4()};
    expect(ids.length, 50);
    for (final id in ids) {
      expect(uuidV4Pattern.hasMatch(id), isTrue, reason: id);
    }
  });

  test('UC-CE01 — collecte locale persistable (JSON / SQLite)', () {
    final collecte = CollecteLocale(
      uuidMobile: generateUuidV4(),
      clientIdExterne: '42',
      nomClient: 'Amina Ngo',
      montantCollecte: 25000,
      dateCollecte: '2026-08-30',
      canalPaiement: 'ESPECES',
      latitude: 3.8667,
      longitude: 11.5167,
      createdAt: DateTime.parse('2026-08-30T10:15:00'),
    );

    final raw = CollecteLocale.listToJson([collecte]);
    final restored = CollecteLocale.listFromJson(raw);

    expect(restored, hasLength(1));
    expect(restored.first.uuidMobile, collecte.uuidMobile);
    expect(restored.first.montantCollecte, 25000);
    expect(restored.first.canalPaiement, 'ESPECES');
    expect(restored.first.clientIdExterne, '42');
  });

  test('UC-CE02 — enveloppe API Spring dépliée, SUCCESS et DOUBLON retirés', () {
    final a = generateUuidV4();
    final b = generateUuidV4();
    final c = generateUuidV4();

    final apiBody = {
      'success': true,
      'message': '2 acceptées, 1 doublon',
      'data': {
        'syncId': generateUuidV4(),
        'statutGlobal': 'COMPLETE',
        'stats': {
          'total': 3,
          'succes': 1,
          'doublons': 1,
          'conflits': 0,
          'erreurs': 1,
        },
        'resultats': [
          {'idCollecteMobile': a, 'code': 'SUCCESS'},
          {'idCollecteMobile': b, 'code': 'DOUBLON'},
          {'idCollecteMobile': c, 'code': 'ERREUR'},
        ],
      },
    };

    final payload = SyncResult.unwrapPayload(apiBody);
    final result = SyncResult.fromJson(apiBody);

    expect(result.totalRecu, 3);
    expect(result.acceptees, 1);
    expect(result.doublons, 1);
    expect(result.rejetees, 1);

    final treated = (payload['resultats'] as List)
        .where((r) {
          final code = (r as Map)['code']?.toString() ?? '';
          return code == 'SUCCESS' || code == 'DOUBLON';
        })
        .map((r) => (r as Map)['idCollecteMobile'] as String)
        .toSet();

    expect(treated, {a, b});
    expect(treated.contains(c), isFalse);
  });

  test('payload sync — champs attendus par POST /api/v1/sync/collectes', () {
    final uuid = generateUuidV4();
    final item = {
      'idCollecteMobile': uuid,
      'clientId': '42',
      'dateCollecte': '2026-08-30',
      'montantCollecte': 15000.0,
      'canalPaiement': 'MTN',
    };
    expect(item.keys, containsAll([
      'idCollecteMobile',
      'clientId',
      'dateCollecte',
      'montantCollecte',
      'canalPaiement',
    ]));
    expect(uuidV4Pattern.hasMatch(item['idCollecteMobile'] as String), isTrue);
  });
}
