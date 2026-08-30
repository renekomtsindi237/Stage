import 'package:flutter_test/flutter_test.dart';
import 'package:microrecouv/core/models/collecte_locale.dart';
import 'package:microrecouv/core/services/agent_service.dart';
import 'package:microrecouv/core/services/alerte_service.dart';
import 'package:microrecouv/core/services/api_service.dart';
import 'package:microrecouv/core/services/client_service.dart';
import 'package:microrecouv/core/services/connectivity_service.dart';
import 'package:microrecouv/core/services/local_database.dart';
import 'package:microrecouv/core/services/offline_catalog_service.dart';
import 'package:microrecouv/core/services/storage_service.dart';
import 'package:microrecouv/core/services/sync_service.dart';
import 'package:microrecouv/core/utils/uuid_v4.dart';

import 'helpers/fake_backend.dart';

/// Simulation réelle : HTTP local + SQLite + bascule vers un second serveur.
void main() {
  late FakeBackend local;
  late FakeBackend prod;
  late LocalDatabase db;
  late ConnectivityService connectivity;
  late ApiService api;
  late ClientService clients;
  late SyncService sync;
  late AgentService agent;
  late OfflineCatalogService catalog;

  final journal = <String>[];

  void step(String message) {
    journal.add(message);
    // ignore: avoid_print
    print(message);
  }

  setUp(() async {
    local = FakeBackend(name: 'LOCAL');
    prod = FakeBackend(name: 'PROD');
    local.clients.addAll([
      {
        'idClient': '42',
        'nom': 'Ngo',
        'prenom': 'Amina',
        'telephone': '690000001',
        'agencePrincipale': 'Nlongkak',
      },
      {
        'idClient': '43',
        'nom': 'Mbarga',
        'prenom': 'Paul',
        'telephone': '690000002',
        'agencePrincipale': 'Mokolo',
      },
      {
        'idClient': '44',
        'nom': 'Essomba',
        'prenom': 'Claire',
        'telephone': '690000003',
        'agencePrincipale': 'Nlongkak',
      },
    ]);
    prod.clients.addAll(local.clients);
    await local.start();
    await prod.start();

    db = await LocalDatabase.memory();
    connectivity = ConnectivityService();
    final storage = StorageService(memory: {
      'access_token': 'sim-token',
      'user_role': 'AGENT',
      'username': 'agent.sim',
    });
    api = ApiService(storage, baseUrl: local.baseUrl);
    clients = ClientService(api, db, connectivity);
    sync = SyncService(api, db);
    agent = AgentService(api, db, connectivity);
    final alertes = AlerteService(api, db, connectivity);
    catalog = OfflineCatalogService(
      clients: clients,
      agent: agent,
      alertes: alertes,
      connectivity: connectivity,
    );
  });

  tearDown(() async {
    await db.close();
    await local.stop();
    await prod.stop();
  });

  test('journée agent : prefetch local → offline → sync → bascule prod', () async {
    // 1. En ligne sur le serveur local : téléchargement du portefeuille
    connectivity.forcedOnline = true;
    final n = await catalog.refresh().then((_) => db.clientCount());
    expect(n, 3);
    step('1. Prefetch LOCAL : $n clients dans SQLite (${local.baseUrl})');

    // 2. Coupure réseau : recherche uniquement locale
    connectivity.forcedOnline = false;
    final found = await clients.searchClients(search: 'ami');
    expect(found.single.nom, 'Ngo');
    step('2. Hors ligne : recherche "ami" → ${found.single.fullName} (SQLite)');

    // 3. Deux collectes + un ping GPS stockés localement
    final c1 = CollecteLocale(
      uuidMobile: generateUuidV4(),
      clientIdExterne: '42',
      nomClient: 'Amina Ngo',
      montantCollecte: 15000,
      dateCollecte: '2026-08-30',
      canalPaiement: 'ESPECES',
      latitude: 3.8667,
      longitude: 11.5167,
      createdAt: DateTime.parse('2026-08-30T08:10:00'),
    );
    final c2 = CollecteLocale(
      uuidMobile: generateUuidV4(),
      clientIdExterne: '43',
      nomClient: 'Paul Mbarga',
      montantCollecte: 22000,
      dateCollecte: '2026-08-30',
      canalPaiement: 'MTN',
      createdAt: DateTime.parse('2026-08-30T09:40:00'),
    );
    await sync.ajouterCollecteLocale(c1);
    await sync.ajouterCollecteLocale(c2);
    await agent.updatePosition(
      latitude: 3.8667,
      longitude: 11.5167,
      source: 'COLLECTE',
      collecteUuid: c1.uuidMobile,
    );
    expect((await db.pendingCollectes()).length, 2);
    expect(await db.gpsPendingCount(), 1);
    expect(local.collectes, isEmpty);
    step('3. Offline : 2 collectes + 1 GPS en outbox, serveur LOCAL encore vide');

    // 4. Retour réseau : transition SQLite → serveur local
    connectivity.forcedOnline = true;
    final first = await sync.syncNow();
    expect(first, isNotNull);
    expect(first!.acceptees, 2);
    expect(local.collectes.length, 2);
    expect(local.positions.length, 1);
    expect(await db.pendingCollectes(), isEmpty);
    expect((await db.syncedCollectes()).length, 2);
    expect(await db.gpsPendingCount(), 0);
    step(
      '4. Sync LOCAL : ${first.acceptees}/2 acceptées, '
      'GPS=${local.positions.length}, journal local=${(await db.syncedCollectes()).length}',
    );

    // 5. Nouvelle collecte hors ligne (reste dans l'outbox)
    connectivity.forcedOnline = false;
    final c3 = CollecteLocale(
      uuidMobile: generateUuidV4(),
      clientIdExterne: '44',
      nomClient: 'Claire Essomba',
      montantCollecte: 8000,
      dateCollecte: '2026-08-30',
      canalPaiement: 'ORANGE',
      createdAt: DateTime.parse('2026-08-30T14:05:00'),
    );
    await sync.ajouterCollecteLocale(c3);
    expect((await db.pendingCollectes()).single.uuidMobile, c3.uuidMobile);
    step('5. Offline à nouveau : 1 collecte (Claire) reste locale');

    // 6. Bascule vers le serveur « en ligne » : outbox conservée, cache vidé
    await sync.switchServer(prod.baseUrl);
    expect(api.baseUrl, prod.baseUrl);
    expect(await db.clientCount(), 0);
    expect((await db.pendingCollectes()).length, 1);
    step('6. Bascule PROD ${prod.baseUrl} : cache clients vidé, outbox conservée');

    connectivity.forcedOnline = true;
    await catalog.refresh();
    expect(await db.clientCount(), 3);
    final second = await sync.syncNow();
    expect(second!.acceptees, 1);
    expect(prod.collectes.length, 1);
    expect(prod.collectes.single['idCollecteMobile'], c3.uuidMobile);
    expect(local.collectes.length, 2);
    expect(await db.pendingCollectes(), isEmpty);
    expect((await db.syncedCollectes()).length, 3);
    step(
      '7. Sync PROD : Claire transférée. '
      'LOCAL=${local.collectes.length} collectes, PROD=${prod.collectes.length}',
    );

    // 8. Idempotence : retour LOCAL, renvoyer un UUID déjà accepté → DOUBLON
    await sync.switchServer(local.baseUrl);
    connectivity.forcedOnline = true;
    await sync.ajouterCollecteLocale(c1);
    final third = await sync.syncNow();
    expect(third!.doublons, 1);
    expect(local.collectes.length, 2);
    expect(prod.collectes.length, 1);
    expect(await db.pendingCollectes(), isEmpty);
    step('8. Réenvoi Amina sur LOCAL : DOUBLON, pas de doublon métier');

    expect(journal.length, 8);
  });
}
