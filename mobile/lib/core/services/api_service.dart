import 'package:dio/dio.dart';
import 'storage_service.dart';
import '../config/app_config.dart';

class ApiException implements Exception {
  final String message;
  final int? statusCode;

  ApiException(this.message, {this.statusCode});

  @override
  String toString() => 'ApiException($statusCode): $message';
}

class ApiService {
  late final Dio _dio;
  final StorageService _storage;
  bool _isRefreshing = false;
  String _baseUrl;

  ApiService(this._storage, {String? baseUrl}) : _baseUrl = baseUrl ?? AppConfig.compileTimeUrl {
    _dio = Dio(
      BaseOptions(
        baseUrl: _baseUrl,
        connectTimeout: const Duration(seconds: 30),
        receiveTimeout: const Duration(seconds: 30),
        sendTimeout: const Duration(seconds: 30),
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
        },
      ),
    );

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: _onRequest,
        onResponse: _onResponse,
        onError: _onError,
      ),
    );

    _dio.interceptors.add(LogInterceptor(
      requestBody: true,
      responseBody: true,
      logPrint: (obj) => print('[API] $obj'),
    ));
  }

  Future<void> _onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    final token = await _storage.getAccessToken();
    if (token != null && token.isNotEmpty) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  void _onResponse(Response response, ResponseInterceptorHandler handler) {
    handler.next(response);
  }

  Future<void> _onError(
    DioException error,
    ErrorInterceptorHandler handler,
  ) async {
    if (error.response?.statusCode == 401 && !_isRefreshing) {
      _isRefreshing = true;
      try {
        final refreshToken = await _storage.getRefreshToken();
        if (refreshToken == null) {
          _isRefreshing = false;
          handler.next(error);
          return;
        }

        final refreshResponse = await _dio.post(
          '/api/v1/auth/refresh',
          data: {'refreshToken': refreshToken},
          options: Options(
            headers: {'Authorization': null},
          ),
        );

        final newToken = refreshResponse.data['accessToken'] as String?;
        final newRefresh = refreshResponse.data['refreshToken'] as String?;

        if (newToken != null) {
          await _storage.saveAccessToken(newToken);
          if (newRefresh != null) {
            await _storage.saveRefreshToken(newRefresh);
          }

          final opts = error.requestOptions;
          opts.headers['Authorization'] = 'Bearer $newToken';
          final retryResponse = await _dio.fetch(opts);
          _isRefreshing = false;
          handler.resolve(retryResponse);
          return;
        }
      } catch (_) {
        await _storage.clearAll();
      } finally {
        _isRefreshing = false;
      }
    }

    handler.next(error);
  }

  Future<T> get<T>(
    String path, {
    Map<String, dynamic>? queryParameters,
    T Function(dynamic)? fromJson,
  }) async {
    try {
      final response = await _dio.get(
        path,
        queryParameters: queryParameters,
      );
      if (fromJson != null) return fromJson(response.data);
      return response.data as T;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  Future<T> post<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    T Function(dynamic)? fromJson,
  }) async {
    try {
      final response = await _dio.post(
        path,
        data: data,
        queryParameters: queryParameters,
      );
      if (fromJson != null) return fromJson(response.data);
      return response.data as T;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  Future<T> put<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    T Function(dynamic)? fromJson,
  }) async {
    try {
      final response = await _dio.put(
        path,
        data: data,
        queryParameters: queryParameters,
      );
      if (fromJson != null) return fromJson(response.data);
      return response.data as T;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  Future<T> patch<T>(
    String path, {
    dynamic data,
    Map<String, dynamic>? queryParameters,
    T Function(dynamic)? fromJson,
  }) async {
    try {
      final response = await _dio.patch(
        path,
        data: data,
        queryParameters: queryParameters,
      );
      if (fromJson != null) return fromJson(response.data);
      return response.data as T;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  Future<T> delete<T>(
    String path, {
    dynamic data,
    T Function(dynamic)? fromJson,
  }) async {
    try {
      final response = await _dio.delete(path, data: data);
      if (fromJson != null) return fromJson(response.data);
      return response.data as T;
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  ApiException _handleDioError(DioException e) {
    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.sendTimeout) {
      return ApiException('Délai de connexion dépassé', statusCode: 408);
    }
    if (e.type == DioExceptionType.connectionError) {
      return ApiException('Impossible de se connecter au serveur', statusCode: 503);
    }

    final statusCode = e.response?.statusCode;
    final data = e.response?.data;
    String message = 'Une erreur est survenue';

    if (data is Map<String, dynamic>) {
      message = data['message'] as String? ??
          data['error'] as String? ??
          message;
    } else if (data is String && data.isNotEmpty) {
      message = data;
    }

    switch (statusCode) {
      case 400:
        return ApiException(message.isEmpty ? 'Requête invalide' : message, statusCode: statusCode);
      case 401:
        return ApiException('Session expirée. Reconnectez-vous.', statusCode: statusCode);
      case 403:
        return ApiException('Accès refusé', statusCode: statusCode);
      case 404:
        return ApiException('Ressource introuvable', statusCode: statusCode);
      case 500:
        return ApiException('Erreur serveur interne', statusCode: statusCode);
      default:
        return ApiException(message, statusCode: statusCode);
    }
  }

  String get baseUrl => _baseUrl;

  void setBaseUrl(String url) {
    _baseUrl = url.trim().replaceAll(RegExp(r'/$'), '');
    _dio.options.baseUrl = _baseUrl;
  }

  /// Sonde courte : le serveur répond-il ? (sans JWT)
  Future<bool> pingHealth() async {
    try {
      final response = await _dio.get(
        '/api/v1/health',
        options: Options(
          sendTimeout: const Duration(seconds: 4),
          receiveTimeout: const Duration(seconds: 4),
        ),
      );
      return response.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<String?> getToken() => _storage.getAccessToken();

  /// Upload multipart (FormData) — utilisé pour les photos de profil.
  Future<T> postMultipart<T>({
    required String path,
    required FormData formData,
    required T Function(dynamic) fromJson,
  }) async {
    final token = await _storage.getAccessToken();
    final response = await _dio.post<Map<String, dynamic>>(
      path,
      data: formData,
      options: Options(
        contentType: 'multipart/form-data',
        headers: {if (token != null) 'Authorization': 'Bearer $token'},
      ),
    );
    final body = response.data;
    if (body == null) throw ApiException('Réponse vide');
    final data = body['data'];
    return fromJson(data);
  }

  /// DELETE avec gestion token — utilisé pour supprimer l'avatar.
  Future<T> deleteAuthenticated<T>({
    required String path,
    required T Function(dynamic) fromJson,
  }) async {
    final response = await _dio.delete<Map<String, dynamic>>(path);
    final body = response.data;
    if (body == null) throw ApiException('Réponse vide');
    final data = body['data'];
    return fromJson(data);
  }

  String get sseUrl => '$baseUrl/api/v1/sse/stream';

  String get sseAlerteUrl => '$baseUrl/api/v1/sse/alertes';
}
