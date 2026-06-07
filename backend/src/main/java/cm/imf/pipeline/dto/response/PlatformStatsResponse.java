package cm.imf.pipeline.dto.response;

public record PlatformStatsResponse(
        long totalImfs,
        long activeImfs,
        long inactiveImfs,
        long totalUsers,
        long newImfsThisMonth
) {}
