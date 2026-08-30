class AgentDashboardData {
  final double objectifJour;
  final double collecteJour;
  final int collectesCount;
  final int clientsVisites;
  final int clientsTotal;
  final bool synchronise;
  final List<AgentAlerte> alertesClients;

  AgentDashboardData({
    required this.objectifJour,
    required this.collecteJour,
    required this.collectesCount,
    required this.clientsVisites,
    required this.clientsTotal,
    required this.synchronise,
    required this.alertesClients,
  });

  factory AgentDashboardData.fromJson(Map<String, dynamic> json) {
    var alertesList = json['alertesClients'] as List<dynamic>? ?? [];
    return AgentDashboardData(
      objectifJour: _parseDouble(json['objectifJour']),
      collecteJour: _parseDouble(json['collecteJour']),
      collectesCount: json['collectesCount'] as int? ?? 0,
      clientsVisites: json['clientsVisites'] as int? ?? 0,
      clientsTotal: json['clientsTotal'] as int? ?? 0,
      synchronise: json['synchronise'] as bool? ?? false,
      alertesClients: alertesList
          .map((e) => AgentAlerte.fromJson(e as Map<String, dynamic>))
          .toList(),
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
        'objectifJour': objectifJour,
        'collecteJour': collecteJour,
        'collectesCount': collectesCount,
        'clientsVisites': clientsVisites,
        'clientsTotal': clientsTotal,
        'synchronise': synchronise,
        'alertesClients': alertesClients.map((e) => e.toJson()).toList(),
      };

  factory AgentDashboardData.empty() {
    return AgentDashboardData(
      objectifJour: 50000.0,
      collecteJour: 0.0,
      collectesCount: 0,
      clientsVisites: 0,
      clientsTotal: 0,
      synchronise: true,
      alertesClients: [],
    );
  }
}

class AgentAlerte {
  final String clientId;
  final String nom;
  final String severite;
  final String message;

  AgentAlerte({
    required this.clientId,
    required this.nom,
    required this.severite,
    required this.message,
  });

  factory AgentAlerte.fromJson(Map<String, dynamic> json) {
    return AgentAlerte(
      clientId: json['clientId'] as String? ?? '',
      nom: json['nom'] as String? ?? '',
      severite: json['severite'] as String? ?? '',
      message: json['message'] as String? ?? '',
    );
  }

  Map<String, dynamic> toJson() => {
        'clientId': clientId,
        'nom': nom,
        'severite': severite,
        'message': message,
      };
}
