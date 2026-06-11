class DossierRecouvrement {
  final int id;
  final String? uid;
  final String idPret;
  final String? nomClient;
  final double montantImpaye;
  final int joursRetard;
  final String categorieCobtac;
  final String phase;
  final bool clos;
  final String dateOuverture;
  final double fraisRecouvrement;
  final String? agentResponsableUsername;

  const DossierRecouvrement({
    required this.id,
    this.uid,
    required this.idPret,
    this.nomClient,
    required this.montantImpaye,
    required this.joursRetard,
    required this.categorieCobtac,
    required this.phase,
    required this.clos,
    required this.dateOuverture,
    required this.fraisRecouvrement,
    this.agentResponsableUsername,
  });

  factory DossierRecouvrement.fromJson(Map<String, dynamic> json) {
    return DossierRecouvrement(
      id: json['id'] as int,
      uid: json['uid'] as String?,
      idPret: json['idPret'] as String? ?? '',
      nomClient: json['nomClient'] as String?,
      montantImpaye: (json['montantImpaye'] as num?)?.toDouble() ?? 0,
      joursRetard: (json['joursRetard'] as num?)?.toInt() ?? 0,
      categorieCobtac: json['categorieCobtac'] as String? ?? '',
      phase: json['phase'] as String? ?? '',
      clos: json['clos'] as bool? ?? false,
      dateOuverture: json['dateOuverture'] as String? ?? '',
      fraisRecouvrement: (json['fraisRecouvrement'] as num?)?.toDouble() ?? 0,
      agentResponsableUsername: json['agentResponsableUsername'] as String?,
    );
  }

  String get phaseLabel {
    const map = {
      'RELANCE_AMIABLE': 'Relance amiable',
      'MEDIATION_AMIABLE': 'Médiation amiable',
      'MISE_EN_DEMEURE': 'Mise en demeure',
      'CONTENTIEUX': 'Contentieux',
      'REECHELONNEMENT': 'Rééchelonnement',
      'PERTE': 'Perte',
    };
    return map[phase] ?? phase;
  }
}

class ActionRecouvrement {
  final int id;
  final String typeAction;
  final String dateAction;
  final String? agentUsername;
  final String? resultat;
  final double? fraisEngages;
  final String? observation;

  const ActionRecouvrement({
    required this.id,
    required this.typeAction,
    required this.dateAction,
    this.agentUsername,
    this.resultat,
    this.fraisEngages,
    this.observation,
  });

  factory ActionRecouvrement.fromJson(Map<String, dynamic> json) {
    return ActionRecouvrement(
      id: json['id'] as int,
      typeAction: json['typeAction'] as String? ?? '',
      dateAction: json['dateAction'] as String? ?? '',
      agentUsername: json['agentUsername'] as String?,
      resultat: json['resultat'] as String?,
      fraisEngages: (json['fraisEngages'] as num?)?.toDouble(),
      observation: json['observation'] as String?,
    );
  }
}
