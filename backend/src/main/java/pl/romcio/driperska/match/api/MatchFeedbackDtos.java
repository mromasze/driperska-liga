package pl.romcio.driperska.match.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;

public final class MatchFeedbackDtos {
    private MatchFeedbackDtos() {}

    public record SubmitFeedbackRequest(UUID upvotePlayerId, UUID downvotePlayerId, String note) {}

    public record FeedbackParticipant(UUID playerId, String nickname, Side side, Role role) {}

    public record MyFeedback(UUID upvotePlayerId, UUID downvotePlayerId, String note) {}

    public record RateableMatch(UUID matchId, Instant completedAt,
                                List<FeedbackParticipant> participants, MyFeedback myFeedback) {}
}
