package pl.romcio.driperska.match.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.match.domain.ApprovalDecision;
import pl.romcio.driperska.match.domain.DrawMode;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.domain.MatchStatus;

/** All request/response payloads for the match lifecycle. */
public final class MatchDtos {

    private MatchDtos() {
    }

    // ---- requests ----

    public record CreateMatchRequest(
            @NotNull UUID seasonId,
            DrawMode drawMode,
            @NotNull @Size(min = 10, max = 10, message = "Pula musi mieć dokładnie 10 graczy")
            List<UUID> playerIds) {
    }

    public record ParticipantResultInput(
            @NotNull UUID playerId,
            @NotNull Role role,
            @NotNull Integer championId,
            @PositiveOrZero int kills,
            @PositiveOrZero int deaths,
            @PositiveOrZero int assists,
            @PositiveOrZero int cs,
            @PositiveOrZero int gold,
            @PositiveOrZero int damageToChampions,
            @PositiveOrZero int visionScore,
            @PositiveOrZero int largestMultiKill) {
    }

    public record SubmitResultsRequest(
            @NotNull Side winningSide,
            @NotNull @Positive Integer durationSeconds,
            String patch,
            @NotNull @Size(min = 10, max = 10) @Valid List<ParticipantResultInput> participants) {
    }

    public record ApproveRequest(
            boolean signatureConfirmed,
            @NotEmpty String signatureName) {
    }

    public record RejectRequest(
            @NotEmpty String reason) {
    }

    public record ReplacePlayerRequest(@NotNull UUID removedPlayerId,
                                       @NotNull UUID addedPlayerId) {}

    // ---- responses ----

    public record BalanceResponse(double blueMmrAvg, double redMmrAvg, double predictedBlueWinPct) {
    }

    public record DrawSlotResponse(UUID playerId, String nickname, Role role, double mmr) {
    }

    public record DrawResponse(
            UUID matchId,
            DrawMode drawMode,
            List<DrawSlotResponse> blue,
            List<DrawSlotResponse> red,
            BalanceResponse balance) {
    }

    public record ParticipantResponse(
            UUID playerId,
            String nickname,
            String avatarUrl,
            Side side,
            Role role,
            Integer championId,
            String championName,
            String championIconUrl,
            int kills,
            int deaths,
            int assists,
            double kda,
            int cs,
            int gold,
            int damageToChampions,
            int visionScore,
            int largestMultiKill,
            Double performanceRating,
            Integer lpAwarded,
            boolean mvp) {
    }

    public record ApprovalResponse(
            ApprovalDecision decision,
            UUID submittedBy,
            Instant submittedAt,
            UUID reviewedBy,
            Instant reviewedAt,
            boolean signatureConfirmed,
            String signatureName,
            String rejectionReason) {
    }

    public record RiotInfoResponse(String tournamentCode, String gameId, String matchId,
                                   Instant lobbyCreatedAt, Instant callbackReceivedAt,
                                   Instant resultsImportedAt, String importError) {
    }

    public record MatchResponse(
            UUID id,
            UUID seasonId,
            MatchStatus status,
            DrawMode drawMode,
            Side winningSide,
            Integer durationSeconds,
            String patch,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            List<ParticipantResponse> participants,
            ApprovalResponse approval,
            RiotInfoResponse riot) {
    }

    public record MatchSummaryResponse(
            UUID id,
            UUID seasonId,
            MatchStatus status,
            Side winningSide,
            Integer durationSeconds,
            Instant createdAt,
            Instant completedAt,
            int participantCount) {
    }

    public record MatchEventResponse(
            MatchEventType type,
            UUID actorAccountId,
            String payloadJson,
            Instant createdAt) {
    }
}
