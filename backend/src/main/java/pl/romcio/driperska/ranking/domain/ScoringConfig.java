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
        int rankingPriorGames,
        double rankingPriorPoints,
        int rankingMinGames,
        double mmrK,
        double mmrKRookie,
        int mmrRookieGames,
        double mmrStart,
        Map<Role, RoleWeights> roleWeights) {

    /** Metric weights for a role; should sum to ~1.0. */
    public record RoleWeights(double kda, double kp, double cs, double damage, double gold, double vision) {
    }

    public static ScoringConfig defaults() {
        return new ScoringConfig(
                10, 4,
                3, 2, 60.0,
                5, 7.0, 5,
                32, 48, 10, 1000.0,
                Map.of(
                        Role.TOP, new RoleWeights(0.25, 0.15, 0.15, 0.25, 0.10, 0.10),
                        Role.JUNGLE, new RoleWeights(0.20, 0.25, 0.10, 0.20, 0.10, 0.15),
                        Role.MID, new RoleWeights(0.20, 0.20, 0.15, 0.30, 0.05, 0.10),
                        Role.ADC, new RoleWeights(0.20, 0.15, 0.20, 0.35, 0.05, 0.05),
                        Role.SUPPORT, new RoleWeights(0.25, 0.30, 0.00, 0.15, 0.05, 0.25)));
    }

    public RoleWeights weightsFor(Role role) {
        return roleWeights.getOrDefault(role, roleWeights.get(Role.MID));
    }
}
