class Alerte {
  final int id;
  final String? message;
  final String? type;
  final String statut;
  final String? dateCreation;
  final String? dateMiseAJour;
  final int? idPret;
  final String? referencePret;
  final String? nomClient;
  final int? joursRetard;
  final double? montantDu;

  Alerte({
    required this.id,
    this.message,
    this.type,
    required this.statut,
    this.dateCreation,
    this.dateMiseAJour,
    this.idPret,
    this.referencePret,
    this.nomClient,
    this.joursRetard,
    this.montantDu,
  });

  factory Alerte.fromJson(Map<String, dynamic> json) {
    return Alerte(
      id: json['id'] as int? ?? 0,
      message: json['message'] as String?,
      type: json['type'] as String?,
      statut: json['statut'] as String? ?? '',
      dateCreation: json['dateCreation'] as String?,
      dateMiseAJour: json['dateMiseAJour'] as String?,
      idPret: json['idPret'] as int?,
      referencePret: json['referencePret'] as String?,
      nomClient: json['nomClient'] as String? ?? json['clientNom'] as String?,
      joursRetard: json['joursRetard'] as int?,
      montantDu: _parseDouble(json['montantDu']),
    );
  }

  static double? _parseDouble(dynamic value) {
    if (value == null) return null;
    if (value is double) return value;
    if (value is int) return value.toDouble();
    if (value is String) return double.tryParse(value);
    return null;
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'message': message,
        'type': type,
        'statut': statut,
        'dateCreation': dateCreation,
        'dateMiseAJour': dateMiseAJour,
        'idPret': idPret,
        'referencePret': referencePret,
        'nomClient': nomClient,
        'joursRetard': joursRetard,
        'montantDu': montantDu,
      };

  bool get isActive => statut == 'ACTIVE' || statut == 'active';
  bool get isEscaladee => statut == 'ESCALADEE' || statut == 'escaladee';
  bool get isCloturee => statut == 'CLOTUREE' || statut == 'cloturee';

  String get displayStatut {
    switch (statut.toUpperCase()) {
      case 'ACTIVE':
        return 'Active';
      case 'ESCALADEE':
        return 'Escaladée';
      case 'CLOTUREE':
        return 'Clôturée';
      case 'TRAITEE':
        return 'Traitée';
      default:
        return statut;
    }
  }
}
