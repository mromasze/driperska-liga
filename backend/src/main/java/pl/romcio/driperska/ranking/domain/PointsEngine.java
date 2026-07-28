package pl.romcio.driperska.ranking.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.ranking.domain.MatchStatsContext.ParticipantInput;

/** Turns Performance Ratings + raw results into League Points, and flags MVP. */
@Component
public class PointsEngine {

    private static final double TIE_EPSILON = 0.01;

    public record PointsBreakdown(int lp, boolean mvp, boolean ace) {
    }

    public Map<UUID, PointsBreakdown> computeLeaguePoints(MatchStatsContext ctx,
                                                          Map<UUID, Double> pr,
                                                          ScoringConfig cfg) {
        Set<UUID> mvpIds = highestPr(ctx, pr, null, 0.0);
        Set<UUID> aceIds = highestPr(ctx, pr, ctx.winningSide().opposite(), cfg.lpAceMinPr());

        Map<UUID, PointsBreakdown> result = new HashMap<>();
        for (ParticipantInput p : ctx.participants()) {
            boolean won = p.side() == ctx.winningSide();
            int lp = won ? cfg.lpWin() : cfg.lpLoss();
            lp += performancePoints(pr.getOrDefault(p.participantId(), 0.0));

            boolean isMvp = mvpIds.contains(p.participantId());
            boolean isAce = !won && aceIds.contains(p.participantId());
            if (isMvp) {
                lp += cfg.lpMvpBonus();
            }
            if (isAce && !isMvp) {
                lp += cfg.lpAceBonus();
            }
            result.put(p.participantId(), new PointsBreakdown(Math.max(0, lp), isMvp, isAce));
        }
        return result;
    }

    public static int performancePoints(double pr) {
        if (pr < 35.0) return -2;
        if (pr < 45.0) return -1;
        if (pr < 55.0) return 0;
        if (pr < 65.0) return 1;
        if (pr < 75.0) return 2;
        return 3;
    }

    private static Set<UUID> highestPr(MatchStatsContext ctx, Map<UUID, Double> pr,
                                       Side sideFilter, double minimumPr) {
        double bestPr = -1;
        for (ParticipantInput p : ctx.participants()) {
            if (sideFilter != null && p.side() != sideFilter) {
                continue;
            }
            double value = pr.getOrDefault(p.participantId(), 0.0);
            if (value > bestPr) {
                bestPr = value;
            }
        }
        if (bestPr < minimumPr) {
            return Set.of();
        }
        Set<UUID> best = new HashSet<>();
        for (ParticipantInput p : ctx.participants()) {
            if ((sideFilter == null || p.side() == sideFilter)
                    && Math.abs(pr.getOrDefault(p.participantId(), 0.0) - bestPr) <= TIE_EPSILON) {
                best.add(p.participantId());
            }
        }
        return Set.copyOf(best);
    }
}
