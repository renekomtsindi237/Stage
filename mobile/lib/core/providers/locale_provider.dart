import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class LocaleProvider extends ChangeNotifier {
  static const _key = 'app_locale';
  static const _supported = ['fr', 'en'];

  Locale _locale = const Locale('fr');

  Locale get locale => _locale;

  LocaleProvider() {
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final code = prefs.getString(_key) ?? 'fr';
    if (_supported.contains(code)) {
      _locale = Locale(code);
      notifyListeners();
    }
  }

  Future<void> setLocale(Locale locale) async {
    if (!_supported.contains(locale.languageCode)) return;
    _locale = locale;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, locale.languageCode);
  }

  void toggle() {
    setLocale(_locale.languageCode == 'fr'
        ? const Locale('en')
        : const Locale('fr'));
  }

  bool get isFrench => _locale.languageCode == 'fr';
}
