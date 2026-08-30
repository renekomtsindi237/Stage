import 'package:flutter_test/flutter_test.dart';
import 'package:microrecouv/core/config/session_policy.dart';

void main() {
  test('session valide pendant 24 h depuis l\'horodatage', () {
    final login = DateTime.parse('2026-08-30T08:15:00');
    expect(
      SessionPolicy.isValid(
        authenticatedAt: login,
        now: login.add(const Duration(hours: 23, minutes: 59)),
      ),
      isTrue,
    );
    expect(
      SessionPolicy.isValid(
        authenticatedAt: login,
        now: login.add(const Duration(hours: 24, minutes: 1)),
      ),
      isFalse,
    );
  });

  test('sessionExpiresAt serveur prime sur authenticatedAt', () {
    final login = DateTime.parse('2026-08-30T08:15:00');
    final expires = DateTime.parse('2026-08-31T08:15:00');
    expect(
      SessionPolicy.isValid(
        authenticatedAt: login,
        expiresAt: expires,
        now: DateTime.parse('2026-08-31T08:14:00'),
      ),
      isTrue,
    );
    expect(
      SessionPolicy.isValid(
        authenticatedAt: login,
        expiresAt: expires,
        now: DateTime.parse('2026-08-31T08:16:00'),
      ),
      isFalse,
    );
  });

  test('sans horodatage la session n\'est pas valable', () {
    expect(SessionPolicy.isValid(), isFalse);
  });
}
