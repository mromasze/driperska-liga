package pl.romcio.driperska.ranking.application;

import static pl.romcio.driperska.ranking.application.PerformanceHistoryService.toContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.match.application.MatchApprovedEvent;
import pl.romcio.driperska.match.application.RankingRecalculationEvent;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.ranking.domain.MatchStatsContext;
import pl.romcio.driperska.ranking.domain.MmrCalculator;
import pl.romcio.driperska.ranking.domain.PerformanceRatingV2Calculator;
import pl.romcio.driperska.ranking.domain.PerformanceRatingV2Calculator.PerformanceHistory;
import pl.romcio.driperska.ranking.domain.PlayerSeasonStats;
import pl.romcio.driperska.ranking.domain.PointsEngine;
import pl.romcio.driperska.ranking.domain.PointsEngine.PointsBreakdown;
import pl.romcio.driperska.ranking.domain.ScoringConfig;
import pl.romcio.driperska.ranking.infra.PlayerSeasonStatsRepository;

/** Applies the scoring engine to approved matches and maintains the per-season ranking. */
@Service
public class RankingService {

    private final MatchRepository matchRepository;
    private final PlayerSeasonStatsRepository statsRepository;
    private final PerformanceRatingV2Calculator ratingCalculator;
    private final PointsEngine pointsEngine;
    private final MmrCalculator mmrCalculator;
    private final ScoringConfigProvider configProvider;
    private final PerformanceHistoryService historyService;

    public RankingService(MatchRepository matchRepository,
                          PlayerSeasonStatsRepository statsRepository,
                          PerformanceRatingV2Calculator ratingCalculator,
                          PointsEngine pointsEngine,
                          MmrCalculator mmrCalculator,
                          ScoringConfigProvider configProvider,
                          PerformanceHistoryService historyService) {
        this.matchRepository = matchRepository;
        this.statsRepository = statsRepository;
        this.ratingCalculator = ratingCalculator;
        this.pointsEngine = pointsEngine;
        this.mmrCalculator = mmrCalculator;
        this.configProvider = configProvider;
        this.historyService = historyService;
    }

    @EventListener
    @Transactional
    public void onMatchApproved(MatchApprovedEvent event) {
        Match match = matchRepository.findById(event.matchId()).orElse(null);
        if (match == null) {
            return;
        }
        ScoringConfig cfg = configProvider.forSeason(match.getSeasonId());
        Map<UUID, PlayerSeasonStats> statsByPlayer = loadSeasonStats(match.getSeasonId());
        scoreMatch(match, cfg, statsByPlayer, historyService.before(match));
        statsRepository.saveAll(statsByPlayer.values());
    }

    @EventListener
    @Transactional
    public void onRecalculationRequested(RankingRecalculationEvent event) {
        recalculateSeason(event.seasonId());
    }

    /**
     * Display-only preview: computes Performance Rating, LP and MVP for a just-submitted match and
     * stores them on the participants so the summary screen shows ratings before approval. Does NOT
     * touch season stats or MMR — those are applied only on approval.
     */
    @EventListener
    @Transactional
    public void onResultsSubmitted(pl.romcio.driperska.match.application.MatchResultsSubmittedEvent event) {
        Match match = matchRepository.findById(event.matchId()).orElse(null);
        if (match == null || match.getWinningSide() == null || match.getParticipants().isEmpty()) {
            return;
        }
        ScoringConfig cfg = configProvider.forSeason(match.getSeasonId());
        MatchStatsContext ctx = toContext(match);
        Map<UUID, Double> pr = ratingCalculator.computePerformance(ctx, cfg, historyService.before(match));
        Map<UUID, PointsBreakdown> points = pointsEngine.computeLeaguePoints(ctx, pr, cfg);
        for (MatchParticipant p : match.getParticipants()) {
            double rating = pr.getOrDefault(p.getId(), 0.0);
            PointsBreakdown breakdown = points.getOrDefault(
                    p.getId(), new PointsBreakdown(0, false, false, false, false));
            p.applyComputed(rating, breakdown.lp(), 0.0, breakdown.mvp(), breakdown.ace());
        }
    }

    /** Wipes and rebuilds a season's ranking from its approved matches, chronologically (Elo needs order). */
    @Transactional
    public void recalculateSeason(UUID seasonId) {
        statsRepository.deleteBySeasonId(seasonId);
        ScoringConfig cfg = configProvider.forSeason(seasonId);
        Map<UUID, PlayerSeasonStats> statsByPlayer = new HashMap<>();
        PerformanceHistory history = new PerformanceHistory();
        List<Match> matches = matchRepository.findByStatusOrderByCompletedAtDesc(MatchStatus.APPROVED).stream()
                .filter(m -> m.getSeasonId().equals(seasonId))
                .sorted(Comparator.comparing(Match::getCompletedAt))
                .toList();
        for (Match match : matches) {
            scoreMatch(match, cfg, statsByPlayer, history);
            history.add(toContext(match));
        }
        statsRepository.saveAll(statsByPlayer.values());
    }

    public record RankingEntry(PlayerSeasonStats stats, double baseScore, double activityBonus,
                               double rankingScore, boolean qualified) {
    }

    @Transactional(readOnly = true)
    public List<RankingEntry> ranking(UUID seasonId) {
        List<PlayerSeasonStats> rows = new ArrayList<>(statsRepository.findBySeasonId(seasonId));
        ScoringConfig cfg = configProvider.forSeason(seasonId);
        int games = rows.stream().mapToInt(PlayerSeasonStats::getGames).sum();
        int points = rows.stream().mapToInt(PlayerSeasonStats::getTotalLp).sum();
        double leagueAverage = games == 0 ? cfg.rankingPriorPoints() : points / (double) games;
        List<RankingEntry> ranking = rows.stream().map(stats -> {
            double baseScore = (stats.getTotalLp() + cfg.rankingPriorGames() * leagueAverage)
                    / (stats.getGames() + (double) cfg.rankingPriorGames());
            double activityBonus = Math.min(stats.getGames(), cfg.rankingActivityMaxGames())
                    * cfg.rankingActivityPointsPerGame();
            return new RankingEntry(stats, round2(baseScore), round2(activityBonus),
                    round2(baseScore + activityBonus),
                    stats.getGames() >= cfg.rankingMinGames());
        }).toList();
        return ranking.stream()
                .sorted(Comparator.comparing(RankingEntry::qualified).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                RankingEntry::rankingScore).reversed())
                        .thenComparing(Comparator.comparingInt(
                                (RankingEntry entry) -> entry.stats().getGames()).reversed())
                        .thenComparing(Comparator.comparingDouble(
                                (RankingEntry entry) -> entry.stats().avgPerformanceRating()).reversed()))
                .toList();
    }

    private void scoreMatch(Match match, ScoringConfig cfg,
                            Map<UUID, PlayerSeasonStats> statsByPlayer,
                            PerformanceHistory history) {
        MatchStatsContext ctx = toContext(match);

        Map<UUID, Double> currentMmr = new HashMap<>();
        Map<UUID, Integer> gamesPlayed = new HashMap<>();
        for (MatchParticipant p : match.getParticipants()) {
            PlayerSeasonStats s = statsByPlayer.get(p.getPlayerId());
            currentMmr.put(p.getPlayerId(), s != null ? s.getMmr() : cfg.mmrStart());
            gamesPlayed.put(p.getPlayerId(), s != null ? s.getGames() : 0);
        }

        Map<UUID, Double> pr = ratingCalculator.computePerformance(ctx, cfg, history);
        Map<UUID, PointsBreakdown> points = pointsEngine.computeLeaguePoints(ctx, pr, cfg);
        Map<UUID, Double> mmrDelta = mmrCalculator.computeMmrDelta(ctx, pr, currentMmr, gamesPlayed, cfg);

        for (MatchParticipant p : match.getParticipants()) {
            double rating = pr.getOrDefault(p.getId(), 0.0);
            PointsBreakdown breakdown = points.getOrDefault(
                    p.getId(), new PointsBreakdown(0, false, false, false, false));
            double delta = mmrDelta.getOrDefault(p.getId(), 0.0);
            boolean won = p.getSide() == match.getWinningSide();
            boolean penta = p.getLargestMultiKill() >= 5;

            p.applyComputed(rating, breakdown.lp(), delta, breakdown.mvp(), breakdown.ace());

            PlayerSeasonStats stats = statsByPlayer.computeIfAbsent(p.getPlayerId(),
                    id -> new PlayerSeasonStats(id, match.getSeasonId(), cfg.mmrStart()));
            stats.addMatch(won, breakdown.lp(), rating, delta,
                    breakdown.mvp(), breakdown.ace(), penta);
        }
    }

    private Map<UUID, PlayerSeasonStats> loadSeasonStats(UUID seasonId) {
        Map<UUID, PlayerSeasonStats> map = new HashMap<>();
        statsRepository.findBySeasonId(seasonId).forEach(s -> map.put(s.getPlayerId(), s));
        return map;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
