class Pret {
  final int idPret;
  final String reference;
  final String? nomClient;
  final int? idClient;
  final double montantInitial;
  final double? montantRestant;
  final double? tauxInteret;
  final String statut;
  final String? dateDebut;
  final String? dateFin;
  final int? joursRetard;
  final int? nombreEcheances;
  final int? echeancesPayees;

  Pret({
    required this.idPret,
    required this.reference,
    this.nomClient,
    this.idClient,
    required this.montantInitial,
    this.montantRestant,
    this.tauxInteret,
    required this.statut,
    this.dateDebut,
    this.dateFin,
    this.joursRetard,
    this.nombreEcheances,
    this.echeancesPayees,
  });

  factory Pret.fromJson(Map<String, dynamic> json) {
    return Pret(
      idPret: json['idPret'] as int? ?? json['id'] as int? ?? 0,
      reference: json['reference'] as String? ?? '',
      nomClient: json['nomClient'] as String? ?? json['clientNom'] as String?,
      idClient: json['idClient'] as int?,
      montantInitial: _parseDouble(json['montantInitial'] ?? json['montant']),
      montantRestant: _parseDouble(json['montantRestant']),
      tauxInteret: _parseDouble(json['tauxInteret']),
      statut: json['statut'] as String? ?? '',
      dateDebut: json['dateDebut'] as String?,
      dateFin: json['dateFin'] as String?,
      joursRetard: json['joursRetard'] as int?,
      nombreEcheances: json['nombreEcheances'] as int?,
      echeancesPayees: json['echeancesPayees'] as int?,
    );
  }

  static double _parseDouble(dynamic value) {
    if (value == null) return 0.0;
    if (value is double) return value;
    if (value is int) return value.toDouble();
    if (value is String) return double.tryParse(value) ?? 0.0;
    return 0.0;
  }

  Map<String, dynamic> toJson() => {
        'idPret': idPret,
        'reference': reference,
        'nomClient': nomClient,
        'idClient': idClient,
        'montantInitial': montantInitial,
        'montantRestant': montantRestant,
        'tauxInteret': tauxInteret,
        'statut': statut,
        'dateDebut': dateDebut,
        'dateFin': dateFin,
        'joursRetard': joursRetard,
        'nombreEcheances': nombreEcheances,
        'echeancesPayees': echeancesPayees,
      };

  bool get isEnRetard => (joursRetard ?? 0) > 0;

  double get progressionPaiement {
    if (nombreEcheances == null || nombreEcheances == 0) return 0;
    return (echeancesPayees ?? 0) / nombreEcheances!;
  }
}
