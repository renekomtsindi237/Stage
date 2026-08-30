import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../models/client.dart';
import '../models/collecte_locale.dart';
import '../utils/uuid_v4.dart';

/// SQLite locale : clients, collectes en attente, cache dashboard / alertes.
class LocalDatabase {
  LocalDatabase._(this._db);

  final Database _db;
  static LocalDatabase? _instance;

  static const _dbName = 'microrecouv_offline.db';

  static Future<LocalDatabase> instance() async {
    if (_instance != null) return _instance!;
    _ensureFactory();
    final dir = await getDatabasesPath();
    final db = await openDatabase(
      p.join(dir, _dbName),
      version: 2,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );
    _instance = LocalDatabase._(db);
    await _instance!._importLegacyPrefs();
    return _instance!;
  }

  @visibleForTesting
  static Future<LocalDatabase> memory() async {
    _ensureFactory();
    final db = await openDatabase(
      inMemoryDatabasePath,
      version: 2,
      onCreate: _onCreate,
    );
    return LocalDatabase._(db);
  }

  static void _ensureFactory() {
    if (kIsWeb) return;
    if (Platform.isWindows || Platform.isLinux) {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
    }
  }

  static Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE clients (
        id_client TEXT PRIMARY KEY,
        nom TEXT NOT NULL,
        prenom TEXT,
        telephone TEXT,
        email TEXT,
        adresse TEXT,
        numero_cni TEXT,
        date_naissance TEXT,
        nombre_prets INTEGER,
        encours_total REAL,
        statut TEXT,
        agence TEXT,
        cached_at TEXT NOT NULL
      )
    ''');
    await db.execute('''
      CREATE TABLE collectes_pending (
        uuid_mobile TEXT PRIMARY KEY,
        client_id TEXT NOT NULL,
        nom_client TEXT,
        montant REAL NOT NULL,
        date_collecte TEXT NOT NULL,
        canal TEXT NOT NULL,
        reference TEXT,
        observation TEXT,
        latitude REAL,
        longitude REAL,
        created_at TEXT NOT NULL,
        last_code TEXT,
        last_error TEXT
      )
    ''');
    await _createV2Tables(db);
    await db.execute('''
      CREATE TABLE kv (
        k TEXT PRIMARY KEY,
        v TEXT NOT NULL,
        cached_at TEXT NOT NULL
      )
    ''');
  }

  static Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      try {
        await db.execute('ALTER TABLE collectes_pending ADD COLUMN last_code TEXT');
      } catch (_) {}
      try {
        await db.execute('ALTER TABLE collectes_pending ADD COLUMN last_error TEXT');
      } catch (_) {}
      await _createV2Tables(db);
    }
  }

  static Future<void> _createV2Tables(Database db) async {
    await db.execute('''
      CREATE TABLE IF NOT EXISTS collectes_synced (
        uuid_mobile TEXT PRIMARY KEY,
        client_id TEXT NOT NULL,
        nom_client TEXT,
        montant REAL NOT NULL,
        date_collecte TEXT NOT NULL,
        canal TEXT NOT NULL,
        reference TEXT,
        observation TEXT,
        latitude REAL,
        longitude REAL,
        created_at TEXT NOT NULL,
        synced_at TEXT NOT NULL,
        code TEXT NOT NULL,
        server_url TEXT
      )
    ''');
    await db.execute('''
      CREATE TABLE IF NOT EXISTS gps_pending (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        payload TEXT NOT NULL,
        created_at TEXT NOT NULL
      )
    ''');
  }

  Future<void> _importLegacyPrefs() async {
    final existing = await _db.rawQuery('SELECT COUNT(*) AS c FROM collectes_pending');
    final count = (existing.first['c'] as int?) ?? 0;
    if (count > 0) return;
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString('collectes_pending');
    if (raw == null || raw.isEmpty) return;
    try {
      for (final c in CollecteLocale.listFromJson(raw)) {
        await insertCollecte(c);
      }
    } catch (_) {}
    final gpsRaw = prefs.getString('gps_position_queue');
    if (gpsRaw != null && gpsRaw.isNotEmpty) {
      try {
        final list = (jsonDecode(gpsRaw) as List<dynamic>).cast<Map<String, dynamic>>();
        for (final item in list) {
          await enqueueGps(item);
        }
        await prefs.remove('gps_position_queue');
      } catch (_) {}
    }
  }

  Future<void> upsertClients(List<Client> clients) async {
    final now = DateTime.now().toIso8601String();
    final batch = _db.batch();
    for (final c in clients) {
      batch.insert(
        'clients',
        {
          'id_client': c.idClient,
          'nom': c.nom,
          'prenom': c.prenom,
          'telephone': c.telephone,
          'email': c.email,
          'adresse': c.adresse,
          'numero_cni': c.numeroCni,
          'date_naissance': c.dateNaissance,
          'nombre_prets': c.nombrePrets,
          'encours_total': c.encoursTotal,
          'statut': c.statut,
          'agence': c.agence,
          'cached_at': now,
        },
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true);
  }

  Future<List<Client>> searchClients(String? query, {int limit = 80}) async {
    final q = query?.trim() ?? '';
    final rows = q.isEmpty
        ? await _db.query('clients', orderBy: 'nom COLLATE NOCASE', limit: limit)
        : await _db.query(
            'clients',
            where:
                'nom LIKE ? OR prenom LIKE ? OR telephone LIKE ? OR id_client LIKE ? OR agence LIKE ?',
            whereArgs: List.filled(5, '%$q%'),
            orderBy: 'nom COLLATE NOCASE',
            limit: limit,
          );
    return rows.map(_clientFromRow).toList();
  }

  Future<Client?> getClient(String id) async {
    final rows = await _db.query(
      'clients',
      where: 'id_client = ?',
      whereArgs: [id],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    return _clientFromRow(rows.first);
  }

  Future<int> clientCount() async {
    final rows = await _db.rawQuery('SELECT COUNT(*) AS c FROM clients');
    return (rows.first['c'] as int?) ?? 0;
  }

  Client _clientFromRow(Map<String, Object?> row) {
    return Client(
      idClient: row['id_client'] as String,
      nom: row['nom'] as String,
      prenom: row['prenom'] as String?,
      telephone: row['telephone'] as String?,
      email: row['email'] as String?,
      adresse: row['adresse'] as String?,
      numeroCni: row['numero_cni'] as String?,
      dateNaissance: row['date_naissance'] as String?,
      nombrePrets: row['nombre_prets'] as int?,
      encoursTotal: (row['encours_total'] as num?)?.toDouble(),
      statut: row['statut'] as String?,
      agence: row['agence'] as String?,
    );
  }

  Future<void> insertCollecte(CollecteLocale c) async {
    await _db.insert(
      'collectes_pending',
      {
        'uuid_mobile': c.uuidMobile,
        'client_id': c.clientIdExterne,
        'nom_client': c.nomClient,
        'montant': c.montantCollecte,
        'date_collecte': c.dateCollecte,
        'canal': c.canalPaiement,
        'reference': c.referenceTransaction,
        'observation': c.observation,
        'latitude': c.latitude,
        'longitude': c.longitude,
        'created_at': c.createdAt.toIso8601String(),
        'last_code': c.lastCode,
        'last_error': c.lastError,
      },
      conflictAlgorithm: ConflictAlgorithm.ignore,
    );
  }

  Future<List<CollecteLocale>> pendingCollectes() async {
    final rows = await _db.query('collectes_pending', orderBy: 'created_at ASC');
    return rows
        .map(
          (row) => CollecteLocale(
            uuidMobile: row['uuid_mobile'] as String,
            clientIdExterne: row['client_id'] as String,
            nomClient: row['nom_client'] as String?,
            montantCollecte: (row['montant'] as num).toDouble(),
            dateCollecte: row['date_collecte'] as String,
            canalPaiement: row['canal'] as String,
            referenceTransaction: row['reference'] as String?,
            observation: row['observation'] as String?,
            latitude: (row['latitude'] as num?)?.toDouble(),
            longitude: (row['longitude'] as num?)?.toDouble(),
            createdAt: DateTime.parse(row['created_at'] as String),
            lastCode: row['last_code'] as String?,
            lastError: row['last_error'] as String?,
          ),
        )
        .toList();
  }

  Future<void> markCollecteError(String uuid, String code, String message) async {
    await _db.update(
      'collectes_pending',
      {'last_code': code, 'last_error': message},
      where: 'uuid_mobile = ?',
      whereArgs: [uuid],
    );
  }

  Future<void> archiveCollecte({
    required CollecteLocale c,
    required String code,
    required String serverUrl,
  }) async {
    await _db.insert(
      'collectes_synced',
      {
        'uuid_mobile': c.uuidMobile,
        'client_id': c.clientIdExterne,
        'nom_client': c.nomClient,
        'montant': c.montantCollecte,
        'date_collecte': c.dateCollecte,
        'canal': c.canalPaiement,
        'reference': c.referenceTransaction,
        'observation': c.observation,
        'latitude': c.latitude,
        'longitude': c.longitude,
        'created_at': c.createdAt.toIso8601String(),
        'synced_at': DateTime.now().toIso8601String(),
        'code': code,
        'server_url': serverUrl,
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<List<CollecteLocale>> syncedCollectes({int limit = 80}) async {
    final rows = await _db.query(
      'collectes_synced',
      orderBy: 'synced_at DESC',
      limit: limit,
    );
    return rows
        .map(
          (row) => CollecteLocale(
            uuidMobile: row['uuid_mobile'] as String,
            clientIdExterne: row['client_id'] as String,
            nomClient: row['nom_client'] as String?,
            montantCollecte: (row['montant'] as num).toDouble(),
            dateCollecte: row['date_collecte'] as String,
            canalPaiement: row['canal'] as String,
            referenceTransaction: row['reference'] as String?,
            observation: row['observation'] as String?,
            latitude: (row['latitude'] as num?)?.toDouble(),
            longitude: (row['longitude'] as num?)?.toDouble(),
            createdAt: DateTime.parse(row['created_at'] as String),
            lastCode: row['code'] as String?,
            syncedAt: DateTime.tryParse(row['synced_at'] as String? ?? ''),
            serverUrl: row['server_url'] as String?,
          ),
        )
        .toList();
  }

  Future<void> deleteCollectes(Set<String> uuids) async {
    if (uuids.isEmpty) return;
    final batch = _db.batch();
    for (final id in uuids) {
      batch.delete('collectes_pending', where: 'uuid_mobile = ?', whereArgs: [id]);
    }
    await batch.commit(noResult: true);
  }

  Future<void> putKv(String key, String value) async {
    await _db.insert(
      'kv',
      {
        'k': key,
        'v': value,
        'cached_at': DateTime.now().toIso8601String(),
      },
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<String?> getKv(String key) async {
    final rows = await _db.query('kv', where: 'k = ?', whereArgs: [key], limit: 1);
    if (rows.isEmpty) return null;
    return rows.first['v'] as String?;
  }

  Future<DateTime?> getKvTime(String key) async {
    final rows = await _db.query('kv', where: 'k = ?', whereArgs: [key], limit: 1);
    if (rows.isEmpty) return null;
    final raw = rows.first['cached_at'] as String?;
    return raw == null ? null : DateTime.tryParse(raw);
  }

  Future<String> deviceId() async {
    final existing = await getKv('device_id');
    if (existing != null && existing.isNotEmpty) return existing;
    String? legacy;
    try {
      final prefs = await SharedPreferences.getInstance();
      legacy = prefs.getString('device_id');
    } catch (_) {}
    final id = (legacy != null && legacy.isNotEmpty) ? legacy : generateUuidV4();
    await putKv('device_id', id);
    return id;
  }

  Future<void> enqueueGps(Map<String, dynamic> payload) async {
    final countRows = await _db.rawQuery('SELECT COUNT(*) AS c FROM gps_pending');
    final count = (countRows.first['c'] as int?) ?? 0;
    if (count >= 500) {
      await _db.delete(
        'gps_pending',
        where: 'id = (SELECT id FROM gps_pending ORDER BY created_at ASC LIMIT 1)',
      );
    }
    await _db.insert('gps_pending', {
      'payload': jsonEncode(payload),
      'created_at': DateTime.now().toIso8601String(),
    });
  }

  Future<List<({int id, Map<String, dynamic> payload})>> pendingGps() async {
    final rows = await _db.query('gps_pending', orderBy: 'created_at ASC');
    return rows
        .map((row) {
          try {
            return (
              id: row['id'] as int,
              payload: jsonDecode(row['payload'] as String) as Map<String, dynamic>,
            );
          } catch (_) {
            return (id: row['id'] as int, payload: <String, dynamic>{});
          }
        })
        .where((e) => e.payload.isNotEmpty)
        .toList();
  }

  Future<void> deleteGps(Set<int> ids) async {
    if (ids.isEmpty) return;
    final batch = _db.batch();
    for (final id in ids) {
      batch.delete('gps_pending', where: 'id = ?', whereArgs: [id]);
    }
    await batch.commit(noResult: true);
  }

  Future<int> gpsPendingCount() async {
    final rows = await _db.rawQuery('SELECT COUNT(*) AS c FROM gps_pending');
    return (rows.first['c'] as int?) ?? 0;
  }

  /// Vide le cache catalogue (clients / dashboard / alertes) sans toucher à l'outbox.
  Future<void> clearCatalogCache() async {
    await _db.delete('clients');
    await _db.delete('kv', where: 'k IN (?, ?, ?)', whereArgs: ['dashboard', 'alertes', 'last_sync']);
  }

  Future<void> putJson(String key, Object value) async {
    await putKv(key, jsonEncode(value));
  }

  Future<Map<String, dynamic>?> getJson(String key) async {
    final raw = await getKv(key);
    if (raw == null) return null;
    try {
      return jsonDecode(raw) as Map<String, dynamic>;
    } catch (_) {
      return null;
    }
  }

  Future<void> close() => _db.close();
}
