import 'dart:math';

/// UUID v4 RFC 4122 (version 4, variant 10xx), généré localement sans réseau.
String generateUuidV4() {
  final r = Random.secure();
  String seg(int len) =>
      List.generate(len, (_) => r.nextInt(16).toRadixString(16)).join();
  final v = (8 + r.nextInt(4)).toRadixString(16);
  return '${seg(8)}-${seg(4)}-4${seg(3)}-$v${seg(3)}-${seg(12)}';
}

final uuidV4Pattern = RegExp(
  r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
);
