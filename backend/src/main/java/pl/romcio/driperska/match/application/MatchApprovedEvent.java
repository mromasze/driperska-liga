package pl.romcio.driperska.match.application;

import java.util.UUID;

/** Published after a match is approved so the ranking (and any notifier) can react. */
public record MatchApprovedEvent(UUID matchId) {
}
