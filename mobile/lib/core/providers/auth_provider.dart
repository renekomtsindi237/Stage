import 'package:flutter/foundation.dart';
import '../models/user.dart';
import '../services/auth_service.dart';
import '../services/api_service.dart';

enum AuthStatus { initial, loading, authenticated, unauthenticated, error }

class AuthProvider extends ChangeNotifier {
  final AuthService _authService;

  AuthStatus _status = AuthStatus.initial;
  User? _currentUser;
  String? _errorMessage;
  String? _pendingOtpEmail;

  AuthProvider(this._authService);

  AuthStatus get status => _status;
  User? get currentUser => _currentUser;
  String? get errorMessage => _errorMessage;
  String? get pendingOtpEmail => _pendingOtpEmail;
  bool get isAuthenticated => _status == AuthStatus.authenticated;
  bool get isLoading => _status == AuthStatus.loading;

  Future<void> checkAuthStatus() async {
    _status = AuthStatus.loading;
    notifyListeners();

    try {
      final loggedIn = await _authService.isLoggedIn();
      if (loggedIn) {
        await _loadCurrentUser();
      } else {
        _status = AuthStatus.unauthenticated;
      }
    } catch (_) {
      _status = AuthStatus.unauthenticated;
    }

    notifyListeners();
  }

  // ── OTP flow ────────────────────────────────────────────────────────────────

  Future<bool> requestOtp(String email) async {
    _status = AuthStatus.loading;
    _errorMessage = null;
    notifyListeners();

    try {
      await _authService.requestOtp(email);
      _pendingOtpEmail = email;
      _status = AuthStatus.unauthenticated;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _status = AuthStatus.error;
      _errorMessage = e.message;
      notifyListeners();
      return false;
    } catch (_) {
      _status = AuthStatus.error;
      _errorMessage = 'Impossible de contacter le serveur. Vérifiez votre réseau.';
      notifyListeners();
      return false;
    }
  }

  Future<bool> verifyOtp(String email, String code) async {
    _status = AuthStatus.loading;
    _errorMessage = null;
    notifyListeners();

    try {
      final response = await _authService.verifyOtp(email, code);
      if (response.role != 'AGENT') {
        await _authService.logout();
        _status = AuthStatus.error;
        _errorMessage = 'Cette application est réservée aux agents terrain.';
        notifyListeners();
        return false;
      }
      _currentUser = User(username: response.username, role: response.role);
      _pendingOtpEmail = null;
      _status = AuthStatus.authenticated;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _status = AuthStatus.error;
      _errorMessage = e.message;
      notifyListeners();
      return false;
    } catch (_) {
      _status = AuthStatus.error;
      _errorMessage = 'Code invalide ou expiré. Veuillez réessayer.';
      notifyListeners();
      return false;
    }
  }

  // ── Classic login (SUPER_ADMIN uniquement) ──────────────────────────────────

  Future<bool> login(String username, String password) async {
    _status = AuthStatus.loading;
    _errorMessage = null;
    notifyListeners();

    try {
      await _authService.login(username, password);
      await _loadCurrentUser();
      _status = AuthStatus.authenticated;
      notifyListeners();
      return true;
    } on ApiException catch (e) {
      _status = AuthStatus.error;
      _errorMessage = e.message;
      notifyListeners();
      return false;
    } catch (_) {
      _status = AuthStatus.error;
      _errorMessage = 'Erreur de connexion. Vérifiez votre réseau.';
      notifyListeners();
      return false;
    }
  }

  Future<void> logout() async {
    await _authService.logout();
    _currentUser = null;
    _pendingOtpEmail = null;
    _status = AuthStatus.unauthenticated;
    _errorMessage = null;
    notifyListeners();
  }

  Future<void> _loadCurrentUser() async {
    try {
      _currentUser = await _authService.getCurrentUser();
      if (_currentUser?.role != 'AGENT') {
        await _authService.logout();
        _currentUser = null;
        _status = AuthStatus.unauthenticated;
        return;
      }
      _status = AuthStatus.authenticated;
    } catch (_) {
      final username = await _authService.getUsername();
      final role = await _authService.getRole();
      if (username != null && role == 'AGENT') {
        _currentUser = User(username: username, role: role!);
        _status = AuthStatus.authenticated;
      } else {
        if (username != null && role != null) {
          await _authService.logout();
        }
        _status = AuthStatus.unauthenticated;
      }
    }
  }

  /// Met à jour l'URL de l'avatar dans le provider après un upload réussi.
  void updateAvatarUrl(String? url) {
    if (_currentUser == null) return;
    _currentUser = _currentUser!.copyWith(avatarUrl: url);
    notifyListeners();
  }

  void clearError() {
    _errorMessage = null;
    if (_status == AuthStatus.error) {
      _status = AuthStatus.unauthenticated;
    }
    notifyListeners();
  }
}
