package pl.romcio.driperska.match.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Match lifecycle states and the allowed transitions between them. */
public enum MatchStatus {
    DRAFT,
    TEAMS_DRAWN,
    DRAFT_READY,  // squad confirmed (Riot off) — waiting for admin to start the internal draft
    DRAFTING,     // internal champion draft (bans/picks) in progress — Riot API disabled
    DRAFTED,      // draft finished, waiting for the players to make the in-game lobby + admin start
    LOBBY_READY,
    LIVE,
    RESULTS_SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED;

    private static final Map<MatchStatus, Set<MatchStatus>> ALLOWED = Map.ofEntries(
            Map.entry(DRAFT, EnumSet.of(TEAMS_DRAWN, CANCELLED)),
            // self = re-roll; DRAFT_READY = Riot off (await admin); LOBBY_READY = Riot on; LIVE = manual start
            Map.entry(TEAMS_DRAWN, EnumSet.of(TEAMS_DRAWN, DRAFT_READY, DRAFTING, LOBBY_READY, LIVE, CANCELLED)),
            Map.entry(DRAFT_READY, EnumSet.of(DRAFTING, TEAMS_DRAWN, LIVE, CANCELLED)),
            Map.entry(DRAFTING, EnumSet.of(DRAFTED, DRAFTING, TEAMS_DRAWN, CANCELLED)),
            Map.entry(DRAFTED, EnumSet.of(DRAFTING, LIVE, LOBBY_READY, CANCELLED)), // DRAFTING = admin reset
            Map.entry(LOBBY_READY, EnumSet.of(TEAMS_DRAWN, LIVE, CANCELLED)),
            Map.entry(LIVE, EnumSet.of(RESULTS_SUBMITTED, CANCELLED)),
            Map.entry(RESULTS_SUBMITTED, EnumSet.of(APPROVED, REJECTED, CANCELLED)),
            Map.entry(REJECTED, EnumSet.of(RESULTS_SUBMITTED, CANCELLED)),
            Map.entry(APPROVED, EnumSet.of(RESULTS_SUBMITTED)), // reopen for correction (admin only)
            Map.entry(CANCELLED, EnumSet.noneOf(MatchStatus.class)));

    public boolean canTransitionTo(MatchStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == CANCELLED;
    }
}
