package pl.romcio.driperska.match.api;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.match.domain.DrawVoteDecision;
import pl.romcio.driperska.match.domain.MatchStatus;

public final class DrawLobbyDtos {
    private DrawLobbyDtos() {}

    public record VoteRequest(@NotNull UUID matchId, @NotNull DrawVoteDecision decision) {}

    public record LobbyPlayer(UUID playerId, String nickname, String avatarUrl, Role role, Side side,
                              Integer championId, boolean captain) {}

    /** One planned step of the draft (for rendering the full ban/pick timeline). */
    public record DraftStepView(Side side, String type) {}

    /** A pending post-draft swap request (position or champion) awaiting the other player's accept. */
    public record DraftSwapView(UUID id, UUID fromPlayerId, UUID toPlayerId, String type) {}

    public record DraftView(
            String status,            // DRAFTING | DONE
            int currentIndex,
            Instant deadline,
            Side currentSide,         // side to act now (null when done)
            String currentType,       // BAN | PICK (null when done)
            UUID blueCaptain, UUID redCaptain,
            UUID currentPlayerId,     // whose turn it is (captain for BAN, next in order for PICK)
            List<UUID> blueOrder, List<UUID> redOrder,  // top→bottom draft order (captain first)
            List<Integer> blueBans, List<Integer> redBans,
            List<DraftStepView> sequence,
            List<DraftSwapView> swaps) {}

    public record DrawLobbyResponse(
            UUID matchId, MatchStatus status, int round, int requiredAccepts, int accepts, int rejects,
            List<UUID> acceptedPlayerIds, List<UUID> rejectedPlayerIds,
            List<LobbyPlayer> blue, List<LobbyPlayer> red, Instant updatedAt,
            String tournamentCode, String riotImportError,
            Instant voteDeadline, DraftView draft) {}
}
