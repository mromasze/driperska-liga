package pl.romcio.driperska.ranking.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.ranking.domain.MatchStatsContext.ParticipantInput;

/** Turns Performance Ratings + raw results into League Points, and flags MVP. */
@Component
public class PointsEngine {

    public record PointsBreakdown(int lp, boolean mvp) {
    }

    public Map<UUID, PointsBreakdown> computeLeaguePoints(MatchStatsContext ctx,
                                                          Map<UUID, Double> pr,
                                                          ScoringConfig cfg) {
        UUID mvpId = highestPr(ctx, pr, null);
        UUID aceId = highestPr(ctx, pr, ctx.winningSide().opposite());

        Map<UUID, PointsBreakdown> result = new HashMap<>();
        for (ParticipantInput p : ctx.participants()) {
            boolean won = p.side() == ctx.winningSide();
            int lp = won ? cfg.lpWin() : cfg.lpLoss();
            lp += (int) Math.round(pr.getOrDefault(p.participantId(), 0.0) / cfg.lpPerformanceDivisor());

            boolean isMvp = p.participantId().equals(mvpId);
            if (isMvp) {
                lp += cfg.lpMvpBonus();
            }
            if (p.participantId().equals(aceId) && !won) {
                lp += cfg.lpAceBonus();
            }
            if (p.largestMultiKill() >= 5) {
                lp += cfg.lpPentaBonus();
            } else if (p.largestMultiKill() == 4) {
                lp += cfg.lpQuadraBonus();
            }
            if (p.deaths() == 0 && (p.kills() + p.assists()) >= 1) {
                lp += cfg.lpFlawlessBonus();
            }
            result.put(p.participantId(), new PointsBreakdown(Math.max(0, lp), isMvp));
        }
        return result;
    }

    private static UUID highestPr(MatchStatsContext ctx, Map<UUID, Double> pr, Side sideFilter) {
        UUID best = null;
        double bestPr = -1;
        for (ParticipantInput p : ctx.participants()) {
            if (sideFilter != null && p.side() != sideFilter) {
                continue;
            }
            double value = pr.getOrDefault(p.participantId(), 0.0);
            if (value > bestPr) {
                bestPr = value;
                best = p.participantId();
            }
        }
        return best;
    }
}
