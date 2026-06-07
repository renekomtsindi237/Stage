package cm.imf.pipeline.dto.response;

import java.util.List;
import java.util.UUID;

public record SyncCollectesResponse(
    int totalRecu,
    int acceptees,
    int doublons,
    int rejetees,
    List<UUID> uuidsAcceptes,
    List<UUID> uuidsDoublons,
    List<RejectionDetail> details
) {
    public record RejectionDetail(UUID uuidMobile, String motif) {}
}
