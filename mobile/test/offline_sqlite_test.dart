import 'package:flutter_test/flutter_test.dart';
import 'package:microrecouv/core/models/client.dart';
import 'package:microrecouv/core/models/collecte_locale.dart';
import 'package:microrecouv/core/services/local_database.dart';
import 'package:microrecouv/core/utils/uuid_v4.dart';

void main() {
  late LocalDatabase db;

  setUp(() async {
    db = await LocalDatabase.memory();
  });

  tearDown(() async {
    await db.close();
  });

  test('portefeuille clients searchable hors ligne', () async {
    await db.upsertClients([
      Client(idClient: '1', nom: 'Ngo', prenom: 'Amina', telephone: '690000001'),
      Client(idClient: '2', nom: 'Mbarga', prenom: 'Paul', telephone: '690000002'),
    ]);

    expect(await db.clientCount(), 2);
    final byName = await db.searchClients('ami');
    expect(byName.single.idClient, '1');
    final byPhone = await db.searchClients('690000002');
    expect(byPhone.single.nom, 'Mbarga');
    final detail = await db.getClient('1');
    expect(detail?.fullName, 'Amina Ngo');
  });

  test('collecte saisie offline puis retirée après SUCCESS', () async {
    final uuid = generateUuidV4();
    await db.insertCollecte(CollecteLocale(
      uuidMobile: uuid,
      clientIdExterne: '1',
      nomClient: 'Amina Ngo',
      montantCollecte: 15000,
      dateCollecte: '2026-08-30',
      canalPaiement: 'ESPECES',
      createdAt: DateTime.parse('2026-08-30T08:00:00'),
    ));

    expect((await db.pendingCollectes()).single.uuidMobile, uuid);
    await db.deleteCollectes({uuid});
    expect(await db.pendingCollectes(), isEmpty);
  });

  test('GPS outbox puis journal de sync après SUCCESS', () async {
    await db.enqueueGps({'latitude': 3.86, 'longitude': 11.51, 'source': 'MOBILE'});
    expect(await db.gpsPendingCount(), 1);
    final gps = await db.pendingGps();
    expect(gps.single.payload['latitude'], 3.86);
    await db.deleteGps({gps.single.id});
    expect(await db.gpsPendingCount(), 0);

    final c = CollecteLocale(
      uuidMobile: generateUuidV4(),
      clientIdExterne: '1',
      nomClient: 'Amina Ngo',
      montantCollecte: 9000,
      dateCollecte: '2026-08-30',
      canalPaiement: 'WAVE',
      createdAt: DateTime.parse('2026-08-30T11:00:00'),
    );
    await db.insertCollecte(c);
    await db.archiveCollecte(c: c, code: 'SUCCESS', serverUrl: 'https://imf.rene.it.com');
    await db.deleteCollectes({c.uuidMobile});
    expect(await db.pendingCollectes(), isEmpty);
    expect((await db.syncedCollectes()).single.lastCode, 'SUCCESS');
  });

  test('cache dashboard / kv survit pour 72 h (lecture locale)', () async {
    await db.putJson('dashboard', {
      'objectifJour': 40000,
      'collecteJour': 12000,
      'collectesCount': 3,
      'clientsVisites': 2,
      'clientsTotal': 40,
      'synchronise': false,
      'alertesClients': [],
    });
    final cached = await db.getJson('dashboard');
    expect(cached?['collecteJour'], 12000);
    expect(await db.getKvTime('dashboard'), isNotNull);
  });
}
