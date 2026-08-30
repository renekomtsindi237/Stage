import 'dart:io';

import 'package:flutter/foundation.dart';

enum ServerProfile { production, staging, local }

/// URLs connues : prod publique, staging VPS, backend local (Docker / IDE).
class AppConfig {
  AppConfig._();

  static const String productionUrl = 'https://imf.rene.it.com';
  static const String stagingUrl = 'http://84.247.128.40:9090';

  /// Surcharge compile-time : `flutter run --dart-define=API_BASE_URL=...`
  static const String compileTimeUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: productionUrl,
  );

  static const String localOverride = String.fromEnvironment(
    'LOCAL_API_URL',
    defaultValue: '',
  );

  static String localUrl() {
    if (localOverride.isNotEmpty) return localOverride;
    if (!kIsWeb && Platform.isAndroid) return 'http://10.0.2.2:8080';
    return 'http://127.0.0.1:8080';
  }

  static String urlFor(ServerProfile profile) {
    switch (profile) {
      case ServerProfile.production:
        return productionUrl;
      case ServerProfile.staging:
        return stagingUrl;
      case ServerProfile.local:
        return localUrl();
    }
  }

  static ServerProfile profileForUrl(String url) {
    final n = _normalize(url);
    if (n == _normalize(productionUrl)) return ServerProfile.production;
    if (n == _normalize(stagingUrl)) return ServerProfile.staging;
    if (n == _normalize(localUrl()) || n.contains('127.0.0.1') || n.contains('10.0.2.2') || n.contains('localhost')) {
      return ServerProfile.local;
    }
    return ServerProfile.staging;
  }

  static String labelFor(ServerProfile profile) {
    switch (profile) {
      case ServerProfile.production:
        return 'En ligne (prod)';
      case ServerProfile.staging:
        return 'Staging';
      case ServerProfile.local:
        return 'Serveur local';
    }
  }

  static String _normalize(String url) =>
      url.trim().replaceAll(RegExp(r'/$'), '');
}
