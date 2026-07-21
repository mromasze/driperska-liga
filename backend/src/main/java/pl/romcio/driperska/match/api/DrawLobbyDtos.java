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
    public record LobbyPlayer(UUID playerId, String nickname, String avatarUrl, Role role, Side side) {}
    public record DrawLobbyResponse(
            UUID matchId, MatchStatus status, int round, int requiredAccepts, int accepts, int rejects,
            List<UUID> acceptedPlayerIds, List<UUID> rejectedPlayerIds,
            List<LobbyPlayer> blue, List<LobbyPlayer> red, Instant updatedAt) {}
}