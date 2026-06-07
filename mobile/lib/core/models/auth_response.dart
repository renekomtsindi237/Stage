class AuthResponse {
  final String accessToken;
  final String refreshToken;
  final String role;
  final String username;
  final int expiresInSeconds;

  AuthResponse({
    required this.accessToken,
    required this.refreshToken,
    required this.role,
    required this.username,
    required this.expiresInSeconds,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    return AuthResponse(
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      role: json['role'] as String,
      username: json['username'] as String,
      expiresInSeconds: json['expiresInSeconds'] as int? ?? 3600,
    );
  }

  Map<String, dynamic> toJson() => {
        'accessToken': accessToken,
        'refreshToken': refreshToken,
        'role': role,
        'username': username,
        'expiresInSeconds': expiresInSeconds,
      };
}
