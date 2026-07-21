package pl.romcio.driperska.match.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Match lifecycle states and the allowed transitions between them. */
public enum MatchStatus {
    DRAFT,
    TEAMS_DRAWN,
    LOBBY_READY,
    LIVE,
    RESULTS_SUBMITTED,
    APPROVED,
    REJECTED,
    CANCELLED;

    private static final Map<MatchStatus, Set<MatchStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(TEAMS_DRAWN, CANCELLED),
            TEAMS_DRAWN, EnumSet.of(TEAMS_DRAWN, LOBBY_READY, LIVE, CANCELLED), // self = re-roll; LIVE = manual start (no Riot)
            LOBBY_READY, EnumSet.of(TEAMS_DRAWN, LIVE, CANCELLED),
            LIVE, EnumSet.of(RESULTS_SUBMITTED, CANCELLED),
            RESULTS_SUBMITTED, EnumSet.of(APPROVED, REJECTED, CANCELLED),
            REJECTED, EnumSet.of(RESULTS_SUBMITTED, CANCELLED),
            APPROVED, EnumSet.of(RESULTS_SUBMITTED), // reopen for correction (admin only)
            CANCELLED, EnumSet.noneOf(MatchStatus.class));

    public boolean canTransitionTo(MatchStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == CANCELLED;
    }
}
