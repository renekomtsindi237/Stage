class KpiSummary {
  final double totalCollectes;
  final int nbCollectes;
  final double encoursPar30;
  final double encoursPar90;
  final int nbAlertesActives;

  KpiSummary({
    required this.totalCollectes,
    required this.nbCollectes,
    required this.encoursPar30,
    required this.encoursPar90,
    required this.nbAlertesActives,
  });

  factory KpiSummary.fromJson(Map<String, dynamic> json) {
    return KpiSummary(
      totalCollectes: _parseDouble(json['totalCollectes']),
      nbCollectes: json['nbCollectes'] as int? ?? 0,
      encoursPar30: _parseDouble(json['encoursPar30']),
      encoursPar90: _parseDouble(json['encoursPar90']),
      nbAlertesActives: json['nbAlertesActives'] as int? ?? 0,
    );
  }

  static double _parseDouble(dynamic value) {
    if (value == null) return 0.0;
    if (value is double) return value;
    if (value is int) return value.toDouble();
    if (value is String) return double.tryParse(value) ?? 0.0;
    return 0.0;
  }

  factory KpiSummary.empty() {
    return KpiSummary(
      totalCollectes: 0,
      nbCollectes: 0,
      encoursPar30: 0,
      encoursPar90: 0,
      nbAlertesActives: 0,
    );
  }

  Map<String, dynamic> toJson() => {
        'totalCollectes': totalCollectes,
        'nbCollectes': nbCollectes,
        'encoursPar30': encoursPar30,
        'encoursPar90': encoursPar90,
        'nbAlertesActives': nbAlertesActives,
      };
}
