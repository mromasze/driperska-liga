package pl.romcio.driperska.ranking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.ranking.domain.MatchStatsContext.ParticipantInput;
import pl.romcio.driperska.ranking.domain.PointsEngine.PointsBreakdown;

class ScoringTest {

    private static final Role[] ROLES = {Role.TOP, Role.JUNGLE, Role.MID, Role.ADC, Role.SUPPORT};

    private final RatingCalculator rating = new RatingCalculator();
    private final PointsEngine points = new PointsEngine();
    private final MmrCalculator mmr = new MmrCalculator();
    private final ScoringConfig cfg = ScoringConfig.defaults();

    private MatchStatsContext sampleMatch() {
        List<ParticipantInput> parts = new ArrayList<>();
        // BLUE wins convincingly, RED loses.
        for (int i = 0; i < 5; i++) {
            parts.add(new ParticipantInput(UUID.randomUUID(), UUID.randomUUID(), Side.BLUE, ROLES[i],
                    8, 2, 9, 220, 13000, 24000, 30, i == 3 ? 5 : 1));
        }
        for (int i = 0; i < 5; i++) {
            parts.add(new ParticipantInput(UUID.randomUUID(), UUID.randomUUID(), Side.RED, ROLES[i],
                    3, 7, 5, 180, 10000, 16000, 22, 1));
        }
        return new MatchStatsContext(Side.BLUE, 1980, parts);
    }

    @Test
    void performanceRatingIsBounded() {
        MatchStatsContext ctx = sampleMatch();
        Map<UUID, Double> pr = rating.computePerformance(ctx, cfg);
        assertThat(pr).hasSize(10);
        assertThat(pr.values()).allSatisfy(v -> assertThat(v).isBetween(0.0, 100.0));
    }

    @Test
    void winnersScoreMoreThanLosersAndOneMvp() {
        MatchStatsContext ctx = sampleMatch();
        Map<UUID, Double> pr = rating.computePerformance(ctx, cfg);
        Map<UUID, PointsBreakdown> lp = points.computeLeaguePoints(ctx, pr, cfg);

        long mvps = lp.values().stream().filter(PointsBreakdown::mvp).count();
        assertThat(mvps).isEqualTo(1);

        // Each participant's LP is non-negative; winners get at least the win base.
        for (ParticipantInput p : ctx.participants()) {
            PointsBreakdown b = lp.get(p.participantId());
            assertThat(b.lp()).isGreaterThanOrEqualTo(0);
            if (p.side() == Side.BLUE) {
                assertThat(b.lp()).isGreaterThanOrEqualTo(cfg.lpWin());
            }
        }
    }

    @Test
    void mmrIsZeroSumWithoutPrModulation() {
        MatchStatsContext ctx = sampleMatch();
        ScoringConfig noMod = new ScoringConfig(cfg.lpWin(), cfg.lpLoss(), cfg.lpPerformanceDivisor(),
                cfg.lpMvpBonus(), cfg.lpAceBonus(), cfg.lpPentaBonus(), cfg.lpQuadraBonus(),
                cfg.lpFlawlessBonus(), cfg.mmrK(), cfg.mmrKRookie(), cfg.mmrRookieGames(),
                cfg.mmrStart(), false, cfg.roleWeights());
        Map<UUID, Double> pr = rating.computePerformance(ctx, noMod);
        Map<UUID, Integer> games = Map.of();
        Map<UUID, Double> delta = mmr.computeMmrDelta(ctx, pr, Map.of(), games, noMod);
        double sum = delta.values().stream().mapToDouble(Double::doubleValue).sum();
        assertThat(sum).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
    }
}
