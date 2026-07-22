package pl.romcio.driperska.ranking.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.ranking.domain.MatchStatsContext.ParticipantInput;

/** Elo-style MMR delta per participant, used only for balancing future team draws. */
@Component
public class MmrCalculator {

    /**
     * @param currentMmr    player MMR before this match (defaults applied by the caller)
     * @param gamesPlayed   games each player had before this match (for the rookie K-factor)
     */
    public Map<UUID, Double> computeMmrDelta(MatchStatsContext ctx,
                                             Map<UUID, Double> pr,
                                             Map<UUID, Double> currentMmr,
                                             Map<UUID, Integer> gamesPlayed,
                                             ScoringConfig cfg) {
        double blueAvg = teamAverage(ctx, Side.BLUE, currentMmr, cfg);
        double redAvg = teamAverage(ctx, Side.RED, currentMmr, cfg);
        double expectedBlue = 1.0 / (1.0 + Math.pow(10, (redAvg - blueAvg) / 400.0));
        double expectedRed = 1.0 - expectedBlue;

        Map<UUID, Double> result = new HashMap<>();
        for (ParticipantInput p : ctx.participants()) {
            boolean blue = p.side() == Side.BLUE;
            double actual = (p.side() == ctx.winningSide()) ? 1.0 : 0.0;
            double expected = blue ? expectedBlue : expectedRed;

            int games = gamesPlayed.getOrDefault(p.playerId(), 0);
            double k = games < cfg.mmrRookieGames() ? cfg.mmrKRookie() : cfg.mmrK();

            double modPr = 1.0;
            if (cfg.mmrPrModulation()) {
                modPr = 0.75 + 0.5 * (pr.getOrDefault(p.participantId(), 50.0) / 100.0);
            }
            result.put(p.participantId(), round2(k * (actual - expected) * modPr));
        }
        return result;
    }

    private static double teamAverage(MatchStatsContext ctx, Side side,
                                      Map<UUID, Double> currentMmr, ScoringConfig cfg) {
        return ctx.team(side).stream()
                .mapToDouble(p -> currentMmr.getOrDefault(p.playerId(), cfg.mmrStart()))
                .average()
                .orElse(cfg.mmrStart());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
