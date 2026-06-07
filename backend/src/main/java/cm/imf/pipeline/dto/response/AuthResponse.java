package cm.imf.pipeline.dto.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String role,
        String username,
        String imfUid,               // null pour SUPER_ADMIN
        String imfCode,              // null pour SUPER_ADMIN
        String imfNom,               // null pour SUPER_ADMIN
        boolean mustChangePassword,
        long expiresIn
) {}
