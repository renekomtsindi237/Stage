import 'dart:convert';

/// Collecte saisie hors ligne par l'agent.
/// Stockée localement (SharedPreferences JSON) en attente de sync.
class CollecteLocale {
  final String uuidMobile;
  final String clientIdExterne;
  final String? nomClient;
  final double montantCollecte;
  final String dateCollecte; // ISO 8601 yyyy-MM-dd
  final String canalPaiement;
  final String? referenceTransaction;
  final String? observation;
  final double? latitude;
  final double? longitude;
  final DateTime createdAt;

  const CollecteLocale({
    required this.uuidMobile,
    required this.clientIdExterne,
    this.nomClient,
    required this.montantCollecte,
    required this.dateCollecte,
    required this.canalPaiement,
    this.referenceTransaction,
    this.observation,
    this.latitude,
    this.longitude,
    required this.createdAt,
  });

  Map<String, dynamic> toJson() => {
        'uuidMobile': uuidMobile,
        'clientIdExterne': clientIdExterne,
        if (nomClient != null) 'nomClient': nomClient,
        'montantCollecte': montantCollecte,
        'dateCollecte': dateCollecte,
        'canalPaiement': canalPaiement,
        if (referenceTransaction != null) 'referenceTransaction': referenceTransaction,
        if (observation != null) 'observation': observation,
        if (latitude != null) 'latitude': latitude,
        if (longitude != null) 'longitude': longitude,
        'createdAt': createdAt.toIso8601String(),
      };

  factory CollecteLocale.fromJson(Map<String, dynamic> json) => CollecteLocale(
        uuidMobile: json['uuidMobile'] as String,
        clientIdExterne: json['clientIdExterne'] as String,
        nomClient: json['nomClient'] as String?,
        montantCollecte: (json['montantCollecte'] as num).toDouble(),
        dateCollecte: json['dateCollecte'] as String,
        canalPaiement: json['canalPaiement'] as String,
        referenceTransaction: json['referenceTransaction'] as String?,
        observation: json['observation'] as String?,
        latitude: json['latitude'] != null ? (json['latitude'] as num).toDouble() : null,
        longitude: json['longitude'] != null ? (json['longitude'] as num).toDouble() : null,
        createdAt: DateTime.parse(json['createdAt'] as String),
      );

  static List<CollecteLocale> listFromJson(String raw) {
    final list = jsonDecode(raw) as List<dynamic>;
    return list.map((e) => CollecteLocale.fromJson(e as Map<String, dynamic>)).toList();
  }

  static String listToJson(List<CollecteLocale> items) =>
      jsonEncode(items.map((e) => e.toJson()).toList());
}

class SyncResult {
  final int totalRecu;
  final int acceptees;
  final int doublons;
  final int rejetees;
  final DateTime syncedAt;

  const SyncResult({
    required this.totalRecu,
    required this.acceptees,
    required this.doublons,
    required this.rejetees,
    required this.syncedAt,
  });

  factory SyncResult.fromJson(Map<String, dynamic> json) {
    // Format cache local : clés plates (totalRecu, acceptees, …)
    if (json.containsKey('totalRecu')) {
      return SyncResult(
        totalRecu: json['totalRecu'] as int? ?? 0,
        acceptees: json['acceptees'] as int? ?? 0,
        doublons:  json['doublons']  as int? ?? 0,
        rejetees:  json['rejetees']  as int? ?? 0,
        syncedAt:  json['syncedAt'] != null
            ? DateTime.parse(json['syncedAt'] as String)
            : DateTime.now(),
      );
    }
    // Format réponse API : data.stats (total, succes, doublons, conflits, erreurs)
    final stats = json['stats'] as Map<String, dynamic>? ?? {};
    final total     = stats['total']    as int? ?? 0;
    final succes    = stats['succes']   as int? ?? 0;
    final doublons  = stats['doublons'] as int? ?? 0;
    final conflits  = stats['conflits'] as int? ?? 0;
    final erreurs   = stats['erreurs']  as int? ?? 0;
    return SyncResult(
      totalRecu: total,
      acceptees: succes,
      doublons:  doublons,
      rejetees:  conflits + erreurs,
      syncedAt:  DateTime.now(),
    );
  }

  String get resume => '$acceptees/${totalRecu} collecte(s) synchronisée(s)';
}
