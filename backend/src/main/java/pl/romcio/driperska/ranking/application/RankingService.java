package pl.romcio.driperska.ranking.application;

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
import pl.romcio.driperska.ranking.domain.MatchStatsContext.ParticipantInput;
import pl.romcio.driperska.ranking.domain.MmrCalculator;
import pl.romcio.driperska.ranking.domain.PlayerSeasonStats;
import pl.romcio.driperska.ranking.domain.PointsEngine;
import pl.romcio.driperska.ranking.domain.PointsEngine.PointsBreakdown;
import pl.romcio.driperska.ranking.domain.RatingCalculator;
import pl.romcio.driperska.ranking.domain.ScoringConfig;
import pl.romcio.driperska.ranking.infra.PlayerSeasonStatsRepository;

/** Applies the scoring engine to approved matches and maintains the per-season ranking. */
@Service
public class RankingService {

    private final MatchRepository matchRepository;
    private final PlayerSeasonStatsRepository statsRepository;
    private final RatingCalculator ratingCalculator;
    private final PointsEngine pointsEngine;
    private final MmrCalculator mmrCalculator;
    private final ScoringConfigProvider configProvider;

    public RankingService(MatchRepository matchRepository,
                          PlayerSeasonStatsRepository statsRepository,
                          RatingCalculator ratingCalculator,
                          PointsEngine pointsEngine,
                          MmrCalculator mmrCalculator,
                          ScoringConfigProvider configProvider) {
        this.matchRepository = matchRepository;
        this.statsRepository = statsRepository;
        this.ratingCalculator = ratingCalculator;
        this.pointsEngine = pointsEngine;
        this.mmrCalculator = mmrCalculator;
        this.configProvider = configProvider;
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
        scoreMatch(match, cfg, statsByPlayer);
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
        Map<UUID, Double> pr = ratingCalculator.computePerformance(ctx, cfg);
        Map<UUID, PointsBreakdown> points = pointsEngine.computeLeaguePoints(ctx, pr, cfg);
        for (MatchParticipant p : match.getParticipants()) {
            double rating = pr.getOrDefault(p.getId(), 0.0);
            PointsBreakdown breakdown = points.getOrDefault(p.getId(), new PointsBreakdown(0, false));
            p.applyComputed(rating, breakdown.lp(), 0.0, breakdown.mvp());
        }
    }

    /** Wipes and rebuilds a season's ranking from its approved matches, chronologically (Elo needs order). */
    @Transactional
    public void recalculateSeason(UUID seasonId) {
        statsRepository.deleteBySeasonId(seasonId);
        ScoringConfig cfg = configProvider.forSeason(seasonId);
        Map<UUID, PlayerSeasonStats> statsByPlayer = new HashMap<>();
        List<Match> matches = matchRepository.findByStatusOrderByCompletedAtDesc(MatchStatus.APPROVED).stream()
                .filter(m -> m.getSeasonId().equals(seasonId))
                .sorted(Comparator.comparing(Match::getCompletedAt))
                .toList();
        for (Match match : matches) {
            scoreMatch(match, cfg, statsByPlayer);
        }
        statsRepository.saveAll(statsByPlayer.values());
    }

    @Transactional(readOnly = true)
    public List<PlayerSeasonStats> ranking(UUID seasonId) {
        List<PlayerSeasonStats> rows = new ArrayList<>(statsRepository.findBySeasonId(seasonId));
        rows.sort(Comparator
                .comparingInt(PlayerSeasonStats::getTotalLp).reversed()
                .thenComparing(Comparator.comparingDouble(PlayerSeasonStats::winRate).reversed())
                .thenComparing(Comparator.comparingDouble(PlayerSeasonStats::avgPerformanceRating).reversed())
                .thenComparing(Comparator.comparingInt(PlayerSeasonStats::getMvpCount).reversed())
                .thenComparingInt(PlayerSeasonStats::getGames));
        return rows;
    }

    private void scoreMatch(Match match, ScoringConfig cfg, Map<UUID, PlayerSeasonStats> statsByPlayer) {
        MatchStatsContext ctx = toContext(match);

        Map<UUID, Double> currentMmr = new HashMap<>();
        Map<UUID, Integer> gamesPlayed = new HashMap<>();
        for (MatchParticipant p : match.getParticipants()) {
            PlayerSeasonStats s = statsByPlayer.get(p.getPlayerId());
            currentMmr.put(p.getPlayerId(), s != null ? s.getMmr() : cfg.mmrStart());
            gamesPlayed.put(p.getPlayerId(), s != null ? s.getGames() : 0);
        }

        Map<UUID, Double> pr = ratingCalculator.computePerformance(ctx, cfg);
        Map<UUID, PointsBreakdown> points = pointsEngine.computeLeaguePoints(ctx, pr, cfg);
        Map<UUID, Double> mmrDelta = mmrCalculator.computeMmrDelta(ctx, pr, currentMmr, gamesPlayed, cfg);

        for (MatchParticipant p : match.getParticipants()) {
            double rating = pr.getOrDefault(p.getId(), 0.0);
            PointsBreakdown breakdown = points.getOrDefault(p.getId(), new PointsBreakdown(0, false));
            double delta = mmrDelta.getOrDefault(p.getId(), 0.0);
            boolean won = p.getSide() == match.getWinningSide();
            boolean penta = p.getLargestMultiKill() >= 5;

            p.applyComputed(rating, breakdown.lp(), delta, breakdown.mvp());

            PlayerSeasonStats stats = statsByPlayer.computeIfAbsent(p.getPlayerId(),
                    id -> new PlayerSeasonStats(id, match.getSeasonId(), cfg.mmrStart()));
            stats.addMatch(won, breakdown.lp(), rating, delta, breakdown.mvp(), penta);
        }
    }

    private Map<UUID, PlayerSeasonStats> loadSeasonStats(UUID seasonId) {
        Map<UUID, PlayerSeasonStats> map = new HashMap<>();
        statsRepository.findBySeasonId(seasonId).forEach(s -> map.put(s.getPlayerId(), s));
        return map;
    }

    private static MatchStatsContext toContext(Match match) {
        List<ParticipantInput> inputs = new ArrayList<>();
        for (MatchParticipant p : match.getParticipants()) {
            inputs.add(new ParticipantInput(
                    p.getId(), p.getPlayerId(), p.getSide(), p.getRole(),
                    p.getKills(), p.getDeaths(), p.getAssists(), p.getCs(), p.getGold(),
                    p.getDamageToChampions(), p.getVisionScore(), p.getLargestMultiKill()));
        }
        int duration = match.getDurationSeconds() != null ? match.getDurationSeconds() : 1800;
        return new MatchStatsContext(match.getWinningSide(), duration, inputs);
    }
}
