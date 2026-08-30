import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Sonde les serveurs réels (prod + staging). Ne fait pas échouer la CI s'ils sont down.
void main() {
  Future<int?> _status(String url) async {
    try {
      final client = HttpClient();
      client.connectionTimeout = const Duration(seconds: 8);
      final req = await client.getUrl(Uri.parse(url));
      final res = await req.close();
      await res.drain<void>();
      client.close(force: true);
      return res.statusCode;
    } catch (_) {
      return null;
    }
  }

  test('sondage health prod + staging', () async {
    final prod = await _status('https://imf.rene.it.com/api/v1/health');
    final staging = await _status('http://84.247.128.40:9090/api/v1/health');
    final local = await _status('http://127.0.0.1:8080/api/v1/health');
    // ignore: avoid_print
    print('HEALTH prod=$prod staging=$staging local=$local');
    expect(true, isTrue);
  });
}
