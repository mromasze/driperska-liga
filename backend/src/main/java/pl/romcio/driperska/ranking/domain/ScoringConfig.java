package pl.romcio.driperska.ranking.domain;

import java.util.Map;
import pl.romcio.driperska.common.domain.Role;

/**
 * Tunable scoring rules. Defaults mirror docs/04-points-and-ranking.md. Wrapped in a record so a
 * season can override it (from JSON) without any code change.
 */
public record ScoringConfig(
        int lpWin,
        int lpLoss,
        int lpMvpBonus,
        int lpAceBonus,
        double lpAceMinPr,
        int lpBestKdaBonus,
        int lpPerfectKdaBonus,
        int rankingPriorGames,
        double rankingPriorPoints,
        int rankingMinGames,
        double rankingActivityPointsPerGame,
        int rankingActivityMaxGames,
        double mmrK,
        double mmrKRookie,
        int mmrRookieGames,
        double mmrStart,
        Map<Role, RoleWeights> roleWeights) {

    /** Metric weights for a role; should sum to ~1.0. */
    public record RoleWeights(double kda, double kp, double cs, double damage, double gold, double vision) {
    }

    public static ScoringConfig defaults() {
        // Role-specific normalization already accounts for different role expectations. Using one
        // league-wide weight profile keeps PR and MVP comparable between roles.
        RoleWeights leagueWeights = new RoleWeights(0.35, 0.20, 0.10, 0.25, 0.05, 0.05);
        return new ScoringConfig(
                10, 4,
                3, 2, 60.0,
                1, 1,
                5, 7.0, 5, 0.10, 20,
                32, 48, 10, 1000.0,
                Map.of(
                        Role.TOP, leagueWeights,
                        Role.JUNGLE, leagueWeights,
                        Role.MID, leagueWeights,
                        Role.ADC, leagueWeights,
                        Role.SUPPORT, leagueWeights));
    }

    public RoleWeights weightsFor(Role role) {
        return roleWeights.getOrDefault(role, roleWeights.get(Role.MID));
    }
}
