class Echeance {
  final int id;
  final int? idPret;
  final int? numero;
  final String? dateEcheance;
  final double montant;
  final double? montantPaye;
  final String statut;
  final String? datePaiement;

  Echeance({
    required this.id,
    this.idPret,
    this.numero,
    this.dateEcheance,
    required this.montant,
    this.montantPaye,
    required this.statut,
    this.datePaiement,
  });

  factory Echeance.fromJson(Map<String, dynamic> json) {
    return Echeance(
      id: json['id'] as int? ?? 0,
      idPret: json['idPret'] as int?,
      numero: json['numero'] as int?,
      dateEcheance: json['dateEcheance'] as String?,
      montant: _parseDouble(json['montant']),
      montantPaye: _parseDouble(json['montantPaye']),
      statut: json['statut'] as String? ?? '',
      datePaiement: json['datePaiement'] as String?,
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
        'id': id,
        'idPret': idPret,
        'numero': numero,
        'dateEcheance': dateEcheance,
        'montant': montant,
        'montantPaye': montantPaye,
        'statut': statut,
        'datePaiement': datePaiement,
      };

  bool get isPaid => statut == 'PAYEE' || statut == 'payee';
  bool get isOverdue => statut == 'EN_RETARD' || statut == 'en_retard';
  bool get isPending => statut == 'EN_ATTENTE' || statut == 'en_attente';

  String get displayStatut {
    switch (statut.toUpperCase()) {
      case 'PAYEE':
        return 'Payée';
      case 'EN_RETARD':
        return 'En retard';
      case 'EN_ATTENTE':
        return 'En attente';
      case 'PARTIELLEMENT_PAYEE':
        return 'Partielle';
      default:
        return statut;
    }
  }
}
