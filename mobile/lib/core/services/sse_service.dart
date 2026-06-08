import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'storage_service.dart';
import 'api_service.dart';

/// Événement SSE reçu depuis le backend Spring Boot.
class SseEvent {
  final String type;
  final String message;
  final dynamic payload;

  const SseEvent({required this.type, required this.message, this.payload});
}

/// Service de connexion SSE (Server-Sent Events) au backend.
///
/// Maintient une connexion persistante vers GET /api/v1/sse/stream?token=...
/// et expose un Stream<SseEvent> consommé par les providers.
/// Reconnexion automatique avec back-off exponentiel (max 30s).
class SseService {
  final StorageService _storage;

  /// Dio dédié SSE — timeout très long pour garder la connexion ouverte.
  late final Dio _dio;

  final _controller = StreamController<SseEvent>.broadcast();
  StreamSubscription? _sub;
  bool _disposed = false;
  int  _retryDelay = 3;

  SseService(this._storage) {
    _dio = Dio(BaseOptions(
      baseUrl: ApiService.baseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(minutes: 10),
    ));
  }

  Stream<SseEvent> get events => _controller.stream;

  Future<void> connect() async {
    if (_disposed) return;
    _retryDelay = 3;
    await _tryConnect();
  }

  Future<void> _tryConnect() async {
    if (_disposed) return;
    final token = await _storage.getAccessToken();
    if (token == null) return;

    try {
      final response = await _dio.get<ResponseBody>(
        '/api/v1/sse/stream',
        queryParameters: {'token': token},
        options: Options(responseType: ResponseType.stream),
      );

      _retryDelay = 3;

      final buffer = StringBuffer();
      String currentType = '';

      _sub = response.data!.stream
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen(
        (line) {
          if (line.startsWith('event:')) {
            currentType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            buffer.write(line.substring(5).trim());
          } else if (line.isEmpty && buffer.isNotEmpty) {
            _handleRawEvent(currentType, buffer.toString());
            buffer.clear();
            currentType = '';
          }
        },
        onError: (_) => _scheduleReconnect(),
        onDone:  ()  => _scheduleReconnect(),
      );
    } catch (_) {
      _scheduleReconnect();
    }
  }

  void _handleRawEvent(String type, String rawData) {
    if (type == 'HEARTBEAT' || type.isEmpty) return;
    try {
      final json    = jsonDecode(rawData) as Map<String, dynamic>;
      final evtType = (json['type'] as String?) ?? type;
      final message = (json['message'] as String?) ?? '';
      final payload = json['payload'];
      _controller.add(SseEvent(type: evtType, message: message, payload: payload));
    } catch (_) {
      // ligne malformée — ignorée
    }
  }

  void _scheduleReconnect() {
    if (_disposed) return;
    _sub?.cancel();
    Future.delayed(Duration(seconds: _retryDelay), () {
      if (_retryDelay < 30) _retryDelay = (_retryDelay * 2).clamp(3, 30);
      _tryConnect();
    });
  }

  void disconnect() {
    _sub?.cancel();
    _sub = null;
  }

  void dispose() {
    _disposed = true;
    _sub?.cancel();
    _controller.close();
  }
}
