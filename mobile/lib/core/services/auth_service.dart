import '../models/auth_response.dart';
import '../models/user.dart';
import 'api_service.dart';
import 'storage_service.dart';

class AuthService {
  final ApiService _api;
  final StorageService _storage;

  AuthService(this._api, this._storage);

  Future<AuthResponse> login(String username, String password) async {
    final response = await _api.post<AuthResponse>(
      '/api/auth/login',
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
      '/api/auth/refresh',
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
      await _api.post<void>('/api/auth/logout', data: {});
    } catch (_) {
      // Logout locally even if server call fails
    } finally {
      await _storage.clearAll();
    }
  }

  Future<User> getCurrentUser() async {
    return _api.get<User>(
      '/api/users/me',
      fromJson: (data) => User.fromJson(data as Map<String, dynamic>),
    );
  }

  Future<bool> isLoggedIn() async {
    return _storage.hasAccessToken();
  }

  Future<String?> getToken() async => _storage.getAccessToken();

  Future<String?> getRole() async => _storage.getRole();

  Future<String?> getUsername() async => _storage.getUsername();
}
