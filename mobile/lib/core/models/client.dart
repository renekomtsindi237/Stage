class Client {
  final String idClient;
  final String nom;
  final String? prenom;
  final String? telephone;
  final String? email;
  final String? adresse;
  final String? numeroCni;
  final String? dateNaissance;
  final int? nombrePrets;
  final double? encoursTotal;
  final String? statut;

  Client({
    required this.idClient,
    required this.nom,
    this.prenom,
    this.telephone,
    this.email,
    this.adresse,
    this.numeroCni,
    this.dateNaissance,
    this.nombrePrets,
    this.encoursTotal,
    this.statut,
  });

  factory Client.fromJson(Map<String, dynamic> json) {
    final rawId = json['id'] ?? json['idClient'];
    return Client(
      idClient: rawId?.toString() ?? '',
      nom: json['nom'] as String? ?? '',
      prenom: json['prenom'] as String?,
      telephone: json['telephone'] as String?,
      email: json['email'] as String?,
      adresse: json['adresse'] as String?,
      numeroCni: json['numeroCni'] as String?,
      dateNaissance: json['dateNaissance'] as String?,
      nombrePrets: json['nombrePrets'] as int?,
      encoursTotal: _parseDouble(json['encoursTotal']),
      statut: json['statut'] as String?,
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
        'idClient': idClient.toString(),
        'nom': nom,
        'prenom': prenom,
        'telephone': telephone,
        'email': email,
        'adresse': adresse,
        'numeroCni': numeroCni,
        'dateNaissance': dateNaissance,
        'nombrePrets': nombrePrets,
        'encoursTotal': encoursTotal,
        'statut': statut,
      };

  String get fullName => prenom != null ? '$prenom $nom' : nom;

  String get initials {
    final parts = fullName.split(' ');
    if (parts.length >= 2) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return fullName.isNotEmpty ? fullName[0].toUpperCase() : '?';
  }
}
