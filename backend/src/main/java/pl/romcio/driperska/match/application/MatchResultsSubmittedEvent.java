package pl.romcio.driperska.match.application;

import java.util.UUID;

/** Fired when a scoreboard is saved (submit or edit), so ratings can be previewed before approval. */
public record MatchResultsSubmittedEvent(UUID matchId) {
}
