package pl.romcio.driperska.match.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PlannedMatchDtos {
    private PlannedMatchDtos() {}

    public record CreatePlannedMatchRequest(@NotNull Instant scheduledAt,
                                            @Size(max = 500) String note) {}

    public record RsvpRequest(@NotNull String response) {} // YES / NO / MAYBE

    public record RsvpEntry(UUID playerId, String nickname, String response) {}

    public record PlannedMatchResponse(
            UUID id, Instant scheduledAt, String note, String status, Instant createdAt,
            int yes, int no, int maybe, String myResponse, List<RsvpEntry> responses) {}

    public record CreatePlannedMatchResult(PlannedMatchResponse planned,
                                           boolean announced, String announceMessage) {}
}
