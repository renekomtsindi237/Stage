class OtpVerifyResponse {
  final String status;
  final String accessToken;
  final String refreshToken;
  final String role;
  final String username;
  final String? imfUid;
  final String? imfCode;
  final String? imfNom;
  final int expiresIn;

  OtpVerifyResponse({
    required this.status,
    required this.accessToken,
    required this.refreshToken,
    required this.role,
    required this.username,
    this.imfUid,
    this.imfCode,
    this.imfNom,
    required this.expiresIn,
  });

  factory OtpVerifyResponse.fromJson(Map<String, dynamic> json) {
    return OtpVerifyResponse(
      status: json['status'] as String? ?? 'AUTHENTICATED',
      accessToken: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String,
      role: json['role'] as String,
      username: json['username'] as String,
      imfUid: json['imfUid'] as String?,
      imfCode: json['imfCode'] as String?,
      imfNom: json['imfNom'] as String?,
      expiresIn: (json['expiresIn'] as num?)?.toInt() ?? 3600,
    );
  }
}
