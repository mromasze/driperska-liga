package pl.romcio.driperska.ranking.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.ranking.domain.MatchStatsContext.ParticipantInput;

/** Turns Performance Ratings + raw results into League Points and match distinctions. */
@Component
public class PointsEngine {

    private static final double TIE_EPSILON = 0.01;

    public record PointsBreakdown(int lp, boolean mvp, boolean ace,
                                  boolean bestKda, boolean perfectKda) {
    }

    /**
     * MVP is the best player of the <em>winning</em> team, ACE the best of the losing one.
     *
     * <p>Until 0.5.4 the MVP was the highest PR in the match regardless of side. Whenever the best
     * player of the game happened to be on the losing team — which is common, PR measures performance
     * and not the result — that one person collected both titles at once and pocketed the biggest
     * bonus in the system (+3) for a game they lost, while the winning team's best player got nothing.
     * Splitting the two by side makes them mutually exclusive by construction and puts the larger
     * bonus back on the winning side.
     */
    public Map<UUID, PointsBreakdown> computeLeaguePoints(MatchStatsContext ctx,
                                                          Map<UUID, Double> pr,
                                                          ScoringConfig cfg) {
        Set<UUID> mvpIds = highestPr(ctx, pr, ctx.winningSide(), 0.0);
        Set<UUID> aceIds = highestPr(ctx, pr, ctx.winningSide().opposite(), cfg.lpAceMinPr());
        Set<UUID> bestKdaIds = highestKda(ctx);

        Map<UUID, PointsBreakdown> result = new HashMap<>();
        for (ParticipantInput p : ctx.participants()) {
            boolean won = p.side() == ctx.winningSide();
            int lp = won ? cfg.lpWin() : cfg.lpLoss();
            lp += performancePoints(pr.getOrDefault(p.participantId(), 0.0));

            boolean isMvp = mvpIds.contains(p.participantId());
            boolean isAce = !won && aceIds.contains(p.participantId());
            boolean isBestKda = bestKdaIds.contains(p.participantId());
            boolean isPerfectKda = isPerfectKda(p);
            if (isMvp) {
                lp += cfg.lpMvpBonus();
            }
            // The two sets are disjoint now that MVP is winners-only; the guard stays as the thing
            // that would keep the bonuses from stacking if that ever changed again.
            if (isAce && !isMvp) {
                lp += cfg.lpAceBonus();
            }
            if (isBestKda) {
                lp += cfg.lpBestKdaBonus();
            }
            if (isPerfectKda) {
                lp += cfg.lpPerfectKdaBonus();
            }
            result.put(p.participantId(), new PointsBreakdown(
                    Math.max(0, lp), isMvp, isAce, isBestKda, isPerfectKda));
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

    public static boolean isPerfectKda(ParticipantInput participant) {
        return participant.deaths() == 0
                && participant.kills() + participant.assists() > 0;
    }

    private static Set<UUID> highestKda(MatchStatsContext ctx) {
        double bestKda = ctx.participants().stream()
                .mapToDouble(PointsEngine::kda)
                .max()
                .orElse(-1.0);
        Set<UUID> best = new HashSet<>();
        for (ParticipantInput participant : ctx.participants()) {
            if (Math.abs(kda(participant) - bestKda) <= TIE_EPSILON) {
                best.add(participant.participantId());
            }
        }
        return Set.copyOf(best);
    }

    private static double kda(ParticipantInput participant) {
        return (participant.kills() + participant.assists())
                / (double) Math.max(1, participant.deaths());
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
