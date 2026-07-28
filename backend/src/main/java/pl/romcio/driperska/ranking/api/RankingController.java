package pl.romcio.driperska.ranking.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;
import pl.romcio.driperska.ranking.application.RankingService;
import pl.romcio.driperska.ranking.application.RankingService.RankingEntry;
import pl.romcio.driperska.ranking.domain.PlayerSeasonStats;
import pl.romcio.driperska.season.application.SeasonService;

@RestController
@RequestMapping("/api/v1/ranking")
public class RankingController {

    private final RankingService rankingService;
    private final SeasonService seasonService;
    private final PlayerRepository playerRepository;

    public RankingController(RankingService rankingService, SeasonService seasonService,
                             PlayerRepository playerRepository) {
        this.rankingService = rankingService;
        this.seasonService = seasonService;
        this.playerRepository = playerRepository;
    }

    @GetMapping
    public List<RankingRowResponse> ranking(@RequestParam(required = false) UUID season) {
        UUID seasonId = season != null ? season : seasonService.current().getId();
        List<RankingEntry> rows = rankingService.ranking(seasonId);
        Map<UUID, Player> players = new HashMap<>();
        playerRepository.findByIdIn(rows.stream().map(row -> row.stats().getPlayerId()).toList())
                .forEach(p -> players.put(p.getId(), p));
        AtomicInteger rank = new AtomicInteger(0);
        return rows.stream().map(entry -> {
            PlayerSeasonStats s = entry.stats();
            Player p = players.get(s.getPlayerId());
            return new RankingRowResponse(
                    rank.incrementAndGet(),
                    s.getPlayerId(),
                    p != null ? p.getNickname() : "?",
                    p != null ? p.getAvatarUrl() : null,
                    s.getTotalLp(), s.getGames(), s.getWins(), s.getLosses(),
                    s.winRate(), s.avgPerformanceRating(), s.getMmr(),
                    s.getMvpCount(), s.getAceCount(), s.getPentaCount(),
                    entry.baseScore(), entry.activityBonus(), entry.rankingScore(),
                    entry.qualified());
        }).toList();
    }

    @PostMapping("/recalculate")
    @PreAuthorize("hasRole('ADMIN')")
    public RecalcResult recalculate(@RequestParam(required = false) UUID season) {
        UUID seasonId = season != null ? season : seasonService.current().getId();
        rankingService.recalculateSeason(seasonId);
        return new RecalcResult(seasonId);
    }

    public record RecalcResult(UUID seasonId) {
    }

    public record RankingRowResponse(
            int rank,
            UUID playerId,
            String nickname,
            String avatarUrl,
            int totalLp,
            int games,
            int wins,
            int losses,
            double winRate,
            double avgPerformanceRating,
            double mmr,
            int mvpCount,
            int aceCount,
            int pentaCount,
            double baseScore,
            double activityBonus,
            double rankingScore,
            boolean qualified) {
    }
}
