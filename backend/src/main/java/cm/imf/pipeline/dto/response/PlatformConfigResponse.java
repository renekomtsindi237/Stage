package cm.imf.pipeline.dto.response;

public record PlatformConfigResponse(
        long   accessTokenExpiryMinutes,
        long   refreshTokenExpiryDays,
        boolean cookieSecure,
        String  smtpHost,
        int     smtpPort,
        String  smtpUser,
        boolean firebaseEnabled,
        int     dbPoolSize,
        String  environment
) {}
