/// Session mobile : 24 h à partir de l'horodatage de connexion (heure locale de travail).
///
/// Fuseau de référence : Africa/Douala (UTC+1, pas d'heure d'été).
/// Exemple : OTP à 08:15 le lundi → nouvel OTP exigé à partir de 08:15 le mardi.
class SessionPolicy {
  SessionPolicy._();

  static const Duration duration = Duration(hours: 24);

  /// Offset Douala par rapport à UTC (pas de DST).
  static const Duration workZoneOffset = Duration(hours: 1);

  static DateTime nowWorkClock() =>
      DateTime.now().toUtc().add(workZoneOffset);

  static DateTime expiryFrom(DateTime authenticatedAt) =>
      authenticatedAt.add(duration);

  static bool isValid({
    DateTime? authenticatedAt,
    DateTime? expiresAt,
    DateTime? now,
  }) {
    final t = now ?? DateTime.now();
    if (expiresAt != null) return !t.isAfter(expiresAt);
    if (authenticatedAt == null) return false;
    return t.isBefore(expiryFrom(authenticatedAt));
  }
}
