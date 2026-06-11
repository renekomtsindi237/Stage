class Delegation {
  final String uid;
  final String typeDelegation;
  final int delegantId;
  final int delegataireId;
  final int? objetId;
  final String? objetType;
  final String? motif;
  final String? roleDelegue;
  final double? montantSeuil;
  final String dateDebut;
  final String? dateFin;
  final bool actif;
  final String createdAt;

  const Delegation({
    required this.uid,
    required this.typeDelegation,
    required this.delegantId,
    required this.delegataireId,
    this.objetId,
    this.objetType,
    this.motif,
    this.roleDelegue,
    this.montantSeuil,
    required this.dateDebut,
    this.dateFin,
    required this.actif,
    required this.createdAt,
  });

  factory Delegation.fromJson(Map<String, dynamic> json) {
    return Delegation(
      uid: json['uid'] as String,
      typeDelegation: json['typeDelegation'] as String,
      delegantId: json['delegantId'] as int,
      delegataireId: json['delegataireId'] as int,
      objetId: json['objetId'] as int?,
      objetType: json['objetType'] as String?,
      motif: json['motif'] as String?,
      roleDelegue: json['roleDelegue'] as String?,
      montantSeuil: (json['montantSeuil'] as num?)?.toDouble(),
      dateDebut: json['dateDebut'] as String,
      dateFin: json['dateFin'] as String?,
      actif: json['actif'] as bool,
      createdAt: json['createdAt'] as String,
    );
  }

  String get typeLabel =>
      typeDelegation == 'REASSIGNATION_DOSSIER' ? 'Réassignation dossier' : "Délégation d'autorité";
}

class AgentCreditItem {
  final String uid;
  final String username;
  final bool actif;

  const AgentCreditItem({
    required this.uid,
    required this.username,
    required this.actif,
  });

  factory AgentCreditItem.fromJson(Map<String, dynamic> json) {
    return AgentCreditItem(
      uid: json['uid'] as String? ?? '',
      username: json['username'] as String? ?? '',
      actif: json['actif'] as bool? ?? true,
    );
  }
}
