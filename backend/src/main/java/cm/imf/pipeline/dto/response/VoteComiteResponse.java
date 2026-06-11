package cm.imf.pipeline.dto.response;

import cm.imf.pipeline.entity.VoteComite;

import java.time.OffsetDateTime;

public record VoteComiteResponse(
        Long id,
        Long comiteId,
        Long votantId,
        String roleVotant,
        String vote,
        String commentaire,
        OffsetDateTime votedAt
) {
    public static VoteComiteResponse from(VoteComite v) {
        return new VoteComiteResponse(
                v.getId(), v.getComiteId(), v.getVotantId(), v.getRoleVotant(),
                v.getVote(), v.getCommentaire(), v.getVotedAt()
        );
    }
}
