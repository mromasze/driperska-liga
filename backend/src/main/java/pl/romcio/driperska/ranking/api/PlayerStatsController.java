package pl.romcio.driperska.ranking.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchParticipantRepository;
import pl.romcio.driperska.ranking.domain.PlayerSeasonStats;
import pl.romcio.driperska.ranking.infra.PlayerSeasonStatsRepository;
import pl.romcio.driperska.season.application.SeasonService;

/** Read-only player profile aggregates: season stats, champion pool and match history. */
@RestController
@RequestMapping("/api/v1/players")
public class PlayerStatsController {

    private final MatchParticipantRepository participantRepository;
    private final PlayerSeasonStatsRepository statsRepository;
    private final ChampionRepository championRepository;
    private final SeasonService seasonService;

    public PlayerStatsController(MatchParticipantRepository participantRepository,
                                 PlayerSeasonStatsRepository statsRepository,
                                 ChampionRepository championRepository,
                                 SeasonService seasonService) {
        this.participantRepository = participantRepository;
        this.statsRepository = statsRepository;
        this.championRepository = championRepository;
        this.seasonService = seasonService;
    }

    @GetMapping("/{id}/stats")
    @Transactional(readOnly = true)
    public PlayerStatsResponse stats(@PathVariable UUID id, @RequestParam(required = false) UUID season) {
        UUID seasonId = season != null ? season : safeCurrentSeason();
        PlayerSeasonStats stats = seasonId == null ? null
                : statsRepository.findByPlayerIdAndSeasonId(id, seasonId).orElse(null);

        List<MatchParticipant> history = participantRepository.findByPlayerAndMatchStatus(id, MatchStatus.APPROVED);
        List<ChampionPoolEntry> pool = championPool(history);

        SeasonAggregate aggregate = stats == null
                ? new SeasonAggregate(0, 0, 0, 0, 0.0, 0.0, 1000.0, 0, 0)
                : new SeasonAggregate(stats.getTotalLp(), stats.getGames(), stats.getWins(),
                stats.getLosses(), stats.winRate(), stats.avgPerformanceRating(), stats.getMmr(),
                stats.getMvpCount(), stats.getPentaCount());
        return new PlayerStatsResponse(id, seasonId, aggregate, pool);
    }

    @GetMapping("/{id}/matches")
    @Transactional(readOnly = true)
    public List<PlayerMatchEntry> matches(@PathVariable UUID id) {
        List<MatchParticipant> history = participantRepository.findByPlayerAndMatchStatus(id, MatchStatus.APPROVED);
        List<PlayerMatchEntry> entries = new ArrayList<>();
        for (MatchParticipant p : history) {
            Champion champion = championName(p.getChampionId());
            boolean won = p.getSide() == p.getMatch().getWinningSide();
            entries.add(new PlayerMatchEntry(
                    p.getMatch().getId(), p.getSide(), p.getRole(), won,
                    p.getChampionId(), champion != null ? champion.getName() : null,
                    champion != null ? champion.getIconUrl() : null,
                    p.getKills(), p.getDeaths(), p.getAssists(),
                    Math.round(p.kda() * 100.0) / 100.0,
                    p.getPerformanceRating(), p.getLpAwarded(), p.isMvp(),
                    p.getMatch().getStartedAt(), p.getMatch().getCompletedAt()));
        }
        return entries;
    }

    private List<ChampionPoolEntry> championPool(List<MatchParticipant> history) {
        Map<Integer, int[]> counts = new LinkedHashMap<>(); // championId -> [games, wins, sumPr*100]
        Map<Integer, Double> prSum = new LinkedHashMap<>();
        for (MatchParticipant p : history) {
            Integer cid = p.getChampionId();
            if (cid == null) {
                continue;
            }
            boolean won = p.getSide() == p.getMatch().getWinningSide();
            int[] c = counts.computeIfAbsent(cid, k -> new int[2]);
            c[0]++;
            if (won) {
                c[1]++;
            }
            prSum.merge(cid, p.getPerformanceRating() == null ? 0.0 : p.getPerformanceRating(), Double::sum);
        }
        List<ChampionPoolEntry> pool = new ArrayList<>();
        counts.forEach((cid, c) -> {
            Champion champion = championName(cid);
            double avgPr = c[0] == 0 ? 0 : Math.round(prSum.get(cid) / c[0] * 100.0) / 100.0;
            double winRate = c[0] == 0 ? 0 : Math.round((double) c[1] / c[0] * 1000.0) / 1000.0;
            pool.add(new ChampionPoolEntry(cid, champion != null ? champion.getName() : null,
                    champion != null ? champion.getIconUrl() : null, c[0], c[1], winRate, avgPr));
        });
        pool.sort((a, b) -> Integer.compare(b.games(), a.games()));
        return pool;
    }

    private Champion championName(Integer championId) {
        return championId == null ? null : championRepository.findById(championId).orElse(null);
    }

    private UUID safeCurrentSeason() {
        try {
            return seasonService.current().getId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public record SeasonAggregate(int totalLp, int games, int wins, int losses, double winRate,
                                  double avgPerformanceRating, double mmr, int mvpCount, int pentaCount) {
    }

    public record ChampionPoolEntry(int championId, String championName, String iconUrl,
                                    int games, int wins, double winRate, double avgPerformanceRating) {
    }

    public record PlayerStatsResponse(UUID playerId, UUID seasonId, SeasonAggregate season,
                                      List<ChampionPoolEntry> championPool) {
    }

    public record PlayerMatchEntry(UUID matchId, Side side, Role role, boolean won,
                                   Integer championId, String championName, String championIconUrl,
                                   int kills, int deaths, int assists, double kda,
                                   Double performanceRating, Integer lpAwarded, boolean mvp,
                                   Instant startedAt, Instant completedAt) {
    }
}
