import '../config/session_policy.dart';
import '../models/auth_response.dart';
import '../models/otp_verify_response.dart';
import '../models/user.dart';
import 'api_service.dart';
import 'storage_service.dart';

class AuthService {
  final ApiService _api;
  final StorageService _storage;

  AuthService(this._api, this._storage);

  Future<void> requestOtp(String email) async {
    await _api.post<void>(
      '/api/v1/auth/request-otp',
      queryParameters: {'email': email},
    );
  }

  Future<OtpVerifyResponse> verifyOtp(String email, String code) async {
    final response = await _api.post<OtpVerifyResponse>(
      '/api/v1/auth/verify-otp',
      data: {'email': email, 'code': code},
      fromJson: (data) => OtpVerifyResponse.fromJson(data as Map<String, dynamic>),
    );

    final now = DateTime.now();
    await _storage.saveAuthData(
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      role: response.role,
      username: response.username,
      authenticatedAt: now,
      sessionExpiresAt:
          response.sessionExpiresAt ?? SessionPolicy.expiryFrom(now),
    );

    return response;
  }

  Future<AuthResponse> login(String username, String password) async {
    final response = await _api.post<AuthResponse>(
      '/api/v1/auth/login',
      data: {'username': username, 'password': password},
      fromJson: (data) => AuthResponse.fromJson(data as Map<String, dynamic>),
    );

    await _storage.saveAuthData(
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      role: response.role,
      username: response.username,
    );

    return response;
  }

  Future<void> refresh() async {
    final refreshToken = await _storage.getRefreshToken();
    if (refreshToken == null) throw ApiException('No refresh token');

    final response = await _api.post<AuthResponse>(
      '/api/v1/auth/refresh',
      data: {'refreshToken': refreshToken},
      fromJson: (data) => AuthResponse.fromJson(data as Map<String, dynamic>),
    );

    await _storage.saveAuthData(
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      role: response.role,
      username: response.username,
    );
  }

  Future<void> logout() async {
    try {
      await _api.post<void>('/api/v1/auth/logout', data: {});
    } catch (_) {
      // Logout locally even if server call fails
    } finally {
      await _storage.clearAll();
    }
  }

  /// Expire la session sans appeler le serveur (fin des 24 h, hors ligne).
  Future<void> expireLocalSession() async {
    await _storage.clearAll();
  }

  Future<User> getCurrentUser() async {
    return _api.get<User>(
      '/api/v1/users/me',
      fromJson: (data) => User.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<bool> isLoggedIn() async {
    if (!await _storage.hasAccessToken()) return false;
    if (!await isSessionValid()) {
      await expireLocalSession();
      return false;
    }
    return true;
  }

  Future<bool> isSessionValid() async {
    return SessionPolicy.isValid(
      authenticatedAt: await _storage.getAuthenticatedAt(),
      expiresAt: await _storage.getSessionExpiresAt(),
    );
  }

  Future<DateTime?> getSessionExpiresAt() => _storage.getSessionExpiresAt();

  Future<String?> getToken() async => _storage.getAccessToken();

  Future<String?> getRole() async => _storage.getRole();

  Future<String?> getUsername() async => _storage.getUsername();
}
