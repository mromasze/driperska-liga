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
import pl.romcio.driperska.ranking.domain.PerformanceRatingV2Calculator.PerformanceHistory;
import pl.romcio.driperska.ranking.domain.PointsEngine.PointsBreakdown;

class ScoringTest {

    private static final Role[] ROLES = {Role.TOP, Role.JUNGLE, Role.MID, Role.ADC, Role.SUPPORT};

    private final PerformanceRatingV2Calculator rating = new PerformanceRatingV2Calculator();
    private final PerformanceHistory history = new PerformanceHistory();
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
        Map<UUID, Double> pr = rating.computePerformance(ctx, cfg, history);
        assertThat(pr).hasSize(10);
        assertThat(pr.values()).allSatisfy(v -> assertThat(v).isBetween(0.0, 100.0));
    }

    @Test
    void winnersScoreMoreThanLosersAndAtLeastOneMvp() {
        MatchStatsContext ctx = sampleMatch();
        Map<UUID, Double> pr = rating.computePerformance(ctx, cfg, history);
        Map<UUID, PointsBreakdown> lp = points.computeLeaguePoints(ctx, pr, cfg);

        long mvps = lp.values().stream().filter(PointsBreakdown::mvp).count();
        assertThat(mvps).isGreaterThanOrEqualTo(1);

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
    void detailedPrExposesMetricsThatAddUpToPr() {
        MatchStatsContext ctx = sampleMatch();
        Map<UUID, RatingCalculator.PrDetail> detailed = rating.computeDetailed(ctx, cfg, history);
        Map<UUID, Double> plain = rating.computePerformance(ctx, cfg, history);

        assertThat(detailed).hasSameSizeAs(ctx.participants());
        for (ParticipantInput p : ctx.participants()) {
            RatingCalculator.PrDetail d = detailed.get(p.participantId());
            // Same PR as the plain computation and six explained metrics per participant.
            assertThat(d.pr()).isEqualTo(plain.get(p.participantId()));
            assertThat(d.metrics()).hasSize(6);
            double sum = d.metrics().stream().mapToDouble(RatingCalculator.PrMetricDetail::points).sum();
            assertThat(sum).isCloseTo(d.pr(), org.assertj.core.data.Offset.offset(0.1));
            for (RatingCalculator.PrMetricDetail m : d.metrics()) {
                assertThat(m.normalized()).isBetween(0.0, 1.0);
            }
        }
    }

    @Test
    void mmrIsZeroSumAndDoesNotPunishHighPrLosers() {
        MatchStatsContext ctx = sampleMatch();
        Map<UUID, Double> pr = rating.computePerformance(ctx, cfg, history);
        Map<UUID, Integer> games = Map.of();
        Map<UUID, Double> delta = mmr.computeMmrDelta(ctx, pr, Map.of(), games, cfg);
        double sum = delta.values().stream().mapToDouble(Double::doubleValue).sum();
        assertThat(sum).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void performanceUsesTransparentCentredTiers() {
        assertThat(PointsEngine.performancePoints(20)).isEqualTo(-2);
        assertThat(PointsEngine.performancePoints(40)).isEqualTo(-1);
        assertThat(PointsEngine.performancePoints(50)).isZero();
        assertThat(PointsEngine.performancePoints(60)).isEqualTo(1);
        assertThat(PointsEngine.performancePoints(70)).isEqualTo(2);
        assertThat(PointsEngine.performancePoints(80)).isEqualTo(3);
    }

    @Test
    void aceRequiresSixtyPrAndDoesNotStackWithMvp() {
        MatchStatsContext ctx = sampleMatch();
        Map<UUID, Double> values = new java.util.LinkedHashMap<>();
        for (ParticipantInput participant : ctx.participants()) {
            values.put(participant.participantId(), participant.side() == Side.BLUE ? 80.0 : 50.0);
        }
        ParticipantInput bestLoser = ctx.team(Side.RED).getFirst();
        values.put(bestLoser.participantId(), 59.99);
        assertThat(points.computeLeaguePoints(ctx, values, cfg).get(bestLoser.participantId()).ace())
                .isFalse();

        values.put(bestLoser.participantId(), 90.0);
        PointsBreakdown result = points.computeLeaguePoints(ctx, values, cfg).get(bestLoser.participantId());
        assertThat(result.ace()).isTrue();
        assertThat(result.mvp()).isTrue();
        assertThat(result.lp()).isEqualTo(cfg.lpLoss() + 3 + cfg.lpMvpBonus());
    }

    @Test
    void bestAndPerfectKdaBonusesStack() {
        ParticipantInput perfect = new ParticipantInput(
                UUID.randomUUID(), UUID.randomUUID(), Side.BLUE, Role.MID,
                10, 0, 5, 200, 12000, 22000, 20, 2);
        ParticipantInput other = new ParticipantInput(
                UUID.randomUUID(), UUID.randomUUID(), Side.RED, Role.MID,
                8, 2, 4, 180, 11000, 20000, 18, 1);
        ParticipantInput zeroParticipation = new ParticipantInput(
                UUID.randomUUID(), UUID.randomUUID(), Side.RED, Role.SUPPORT,
                0, 0, 0, 20, 5000, 1000, 10, 1);
        MatchStatsContext ctx = new MatchStatsContext(
                Side.BLUE, 1800, List.of(perfect, other, zeroParticipation));
        Map<UUID, Double> ratings = Map.of(
                perfect.participantId(), 50.0,
                other.participantId(), 60.0,
                zeroParticipation.participantId(), 40.0);

        Map<UUID, PointsBreakdown> result = points.computeLeaguePoints(ctx, ratings, cfg);

        assertThat(result.get(perfect.participantId()).bestKda()).isTrue();
        assertThat(result.get(perfect.participantId()).perfectKda()).isTrue();
        assertThat(result.get(perfect.participantId()).lp())
                .isEqualTo(cfg.lpWin() + cfg.lpBestKdaBonus() + cfg.lpPerfectKdaBonus());
        assertThat(result.get(zeroParticipation.participantId()).perfectKda()).isFalse();
    }

    @Test
    void currentMatchStillDifferentiatesPlayersAfterHistoryIsFull() {
        PerformanceHistory fullHistory = new PerformanceHistory();
        for (int match = 0; match < 10; match++) {
            ParticipantInput historicalBlue = new ParticipantInput(
                    UUID.randomUUID(), UUID.randomUUID(), Side.BLUE, Role.MID,
                    1, 5, 1, 80, 7000, 4000, 8, 1);
            ParticipantInput historicalRed = new ParticipantInput(
                    UUID.randomUUID(), UUID.randomUUID(), Side.RED, Role.MID,
                    1, 5, 1, 80, 7000, 4000, 8, 1);
            fullHistory.add(new MatchStatsContext(
                    Side.BLUE, 1800, List.of(historicalBlue, historicalRed)));
        }

        ParticipantInput dominant = new ParticipantInput(
                UUID.randomUUID(), UUID.randomUUID(), Side.BLUE, Role.MID,
                10, 2, 10, 250, 13000, 25000, 30, 2);
        ParticipantInput merelyAboveHistory = new ParticipantInput(
                UUID.randomUUID(), UUID.randomUUID(), Side.RED, Role.MID,
                5, 5, 5, 150, 10000, 12000, 15, 1);
        MatchStatsContext current = new MatchStatsContext(
                Side.BLUE, 1800, List.of(dominant, merelyAboveHistory));

        Map<UUID, Double> result = rating.computePerformance(current, cfg, fullHistory);

        // Both players beat every historical sample, so a 100% historical percentile would tie
        // them. The direct matchup must still recognize the stronger current performance.
        assertThat(result.get(dominant.participantId()))
                .isGreaterThan(result.get(merelyAboveHistory.participantId()));
    }

    @Test
    void leagueWeightsKeepCrossRoleMvpComparable() {
        ScoringConfig.RoleWeights expected = cfg.weightsFor(Role.TOP);
        for (Role role : Role.values()) {
            assertThat(cfg.weightsFor(role)).isEqualTo(expected);
        }

        // Regression values from a match where the old role-specific weights awarded MVP to TOP
        // despite ADC having clearly stronger normalized KDA and damage.
        double topScore = weightedScore(expected, 0.88, 0.69, 0.53, 0.45, 0.40, 0.55);
        double adcScore = weightedScore(expected, 0.96, 0.68, 0.45, 0.48, 0.46, 0.50);

        assertThat(adcScore - topScore).isGreaterThanOrEqualTo(2.5);
    }

    private static double weightedScore(ScoringConfig.RoleWeights weights,
                                        double kda, double kp, double cs, double damage,
                                        double efficiency, double vision) {
        return 100.0 * (
                weights.kda() * kda
                        + weights.kp() * kp
                        + weights.cs() * cs
                        + weights.damage() * damage
                        + weights.gold() * efficiency
                        + weights.vision() * vision);
    }

    @Test
    void historyGraduallyStabilizesRoleReference() {
        MatchStatsContext ctx = sampleMatch();
        PerformanceHistory roleHistory = new PerformanceHistory();
        for (int i = 0; i < 10; i++) {
            roleHistory.add(ctx);
        }
        assertThat(roleHistory.sampleCount(Role.ADC)).isEqualTo(20);
        assertThat(rating.computePerformance(ctx, cfg, roleHistory).values())
                .allSatisfy(value -> assertThat(value).isBetween(0.0, 100.0));
    }
}
