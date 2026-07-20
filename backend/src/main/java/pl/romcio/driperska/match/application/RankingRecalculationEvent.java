package pl.romcio.driperska.match.application;

import java.util.UUID;

/** Requests a full recompute of a season's ranking (e.g. after a match is reopened). */
public record RankingRecalculationEvent(UUID seasonId) {
}
