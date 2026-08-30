import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class StorageService {
  static const _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock_this_device,
    ),
  );

  static const _keyAccessToken = 'access_token';
  static const _keyRefreshToken = 'refresh_token';
  static const _keyRole = 'user_role';
  static const _keyUsername = 'username';
  static const _keyAuthenticatedAt = 'session_authenticated_at';
  static const _keySessionExpiresAt = 'session_expires_at';

  final Map<String, String>? _memory;

  StorageService({Map<String, String>? memory}) : _memory = memory;

  Future<void> _write(String key, String value) async {
    if (_memory != null) {
      _memory![key] = value;
      return;
    }
    await _storage.write(key: key, value: value);
  }

  Future<String?> _read(String key) async {
    if (_memory != null) return _memory![key];
    return _storage.read(key: key);
  }

  // Access Token
  Future<void> saveAccessToken(String token) async {
    await _write(_keyAccessToken, token);
  }

  Future<String?> getAccessToken() async {
    return _read(_keyAccessToken);
  }

  // Refresh Token
  Future<void> saveRefreshToken(String token) async {
    await _write(_keyRefreshToken, token);
  }

  Future<String?> getRefreshToken() async {
    return _read(_keyRefreshToken);
  }

  // Role
  Future<void> saveRole(String role) async {
    await _write(_keyRole, role);
  }

  Future<String?> getRole() async {
    return _read(_keyRole);
  }

  // Username
  Future<void> saveUsername(String username) async {
    await _write(_keyUsername, username);
  }

  Future<String?> getUsername() async {
    return _read(_keyUsername);
  }

  Future<void> saveAuthenticatedAt(DateTime at) async {
    await _write(_keyAuthenticatedAt, at.toIso8601String());
  }

  Future<DateTime?> getAuthenticatedAt() async {
    final raw = await _read(_keyAuthenticatedAt);
    return raw == null ? null : DateTime.tryParse(raw);
  }

  Future<void> saveSessionExpiresAt(DateTime at) async {
    await _write(_keySessionExpiresAt, at.toIso8601String());
  }

  Future<DateTime?> getSessionExpiresAt() async {
    final raw = await _read(_keySessionExpiresAt);
    return raw == null ? null : DateTime.tryParse(raw);
  }

  // Clear all (logout)
  Future<void> clearAll() async {
    if (_memory != null) {
      _memory!.clear();
      return;
    }
    await _storage.deleteAll();
  }

  // Check if logged in
  Future<bool> hasAccessToken() async {
    final token = await getAccessToken();
    return token != null && token.isNotEmpty;
  }

  // Save all auth data at once
  Future<void> saveAuthData({
    required String accessToken,
    required String refreshToken,
    required String role,
    required String username,
    DateTime? authenticatedAt,
    DateTime? sessionExpiresAt,
  }) async {
    await Future.wait([
      saveAccessToken(accessToken),
      saveRefreshToken(refreshToken),
      saveRole(role),
      saveUsername(username),
    ]);
    if (authenticatedAt != null) {
      await saveAuthenticatedAt(authenticatedAt);
    }
    if (sessionExpiresAt != null) {
      await saveSessionExpiresAt(sessionExpiresAt);
    }
  }
}
