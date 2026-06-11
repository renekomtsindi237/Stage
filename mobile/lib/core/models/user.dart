class User {
  final int? id;
  final String username;
  final String? email;
  final String? nom;
  final String? prenom;
  final String role;
  final bool? actif;

  User({
    this.id,
    required this.username,
    this.email,
    this.nom,
    this.prenom,
    required this.role,
    this.actif,
  });

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] as int?,
      username: json['username'] as String? ?? '',
      email: json['email'] as String?,
      nom: json['nom'] as String?,
      prenom: json['prenom'] as String?,
      role: json['role'] as String? ?? '',
      actif: json['actif'] as bool?,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'username': username,
        'email': email,
        'nom': nom,
        'prenom': prenom,
        'role': role,
        'actif': actif,
      };

  String get fullName {
    if (nom != null && prenom != null) return '$prenom $nom';
    if (nom != null) return nom!;
    if (prenom != null) return prenom!;
    return username;
  }

  String get displayRole {
    switch (role) {
      case 'AGENT':
        return 'Agent Recouvrement';
      case 'AGENT_CREDIT':
        return 'Chargé de Clientèle';
      case 'CHEF_AGENCE':
        return "Chef d'Agence";
      case 'ANALYSTE_ENGAGEMENTS':
        return 'Analyste des Engagements';
      case 'AGENT_SAISIE':
        return 'Agent de Saisie';
      case 'CAISSIER':
        return 'Caissier';
      case 'RESPONSABLE_RECOUVREMENT':
        return 'Responsable Recouvrement';
      case 'ANALYSTE':
        return 'Analyste / Risk Manager';
      case 'DIRECTEUR':
        return 'Directeur';
      case 'DSI':
        return 'DSI';
      case 'SUPER_ADMIN':
        return 'Super Administrateur';
      default:
        return role;
    }
  }

  bool get isManager {
    return role == 'RESPONSABLE_RECOUVREMENT' ||
        role == 'ANALYSTE' ||
        role == 'ANALYSTE_ENGAGEMENTS' ||
        role == 'CHEF_AGENCE' ||
        role == 'DIRECTEUR' ||
        role == 'DSI';
  }

  bool get isCreditRole {
    return role == 'AGENT_CREDIT' ||
        role == 'CHEF_AGENCE' ||
        role == 'ANALYSTE_ENGAGEMENTS';
  }

  bool get isCaisseRole {
    return role == 'CAISSIER';
  }

  bool get isBackOfficeRole {
    return role == 'AGENT_SAISIE' || role == 'CAISSIER';
  }
}
