package pl.romcio.driperska.ranking.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchParticipantRepository;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.ranking.infra.PlayerSeasonStatsRepository;

/**
 * Season-wide totals for the landing page.
 *
 * <p>Kept out of {@link RankingService}, which is about scoring individual matches: this only reads,
 * and it reads across every scored match at once. Two aggregate queries rather than loading matches,
 * because the numbers grow with every game played and the home page asks for them on each visit.
 * {@code PlayerSeasonStats} cannot answer this — it tracks LP, wins and MMR, not kills.
 */
@Service
public class LeagueSummaryService {

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;
    private final PlayerSeasonStatsRepository statsRepository;

    public LeagueSummaryService(MatchRepository matchRepository,
                                MatchParticipantRepository participantRepository,
                                PlayerSeasonStatsRepository statsRepository) {
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.statsRepository = statsRepository;
    }

    /**
     * @param players            how many people have played at least one scored match this season
     * @param avgDurationSeconds null until the first match with a recorded duration
     */
    public record LeagueSummary(
            UUID seasonId,
            int matches,
            int players,
            long kills,
            long deaths,
            long assists,
            long cs,
            long gold,
            long damageToChampions,
            long visionScore,
            int pentakills,
            long playtimeSeconds,
            Integer avgDurationSeconds) {
    }

    @Transactional(readOnly = true)
    public LeagueSummary forSeason(UUID seasonId) {
        var playtime = matchRepository.playtimeForSeason(MatchStatus.APPROVED, seasonId);
        var totals = participantRepository.totalsForSeason(MatchStatus.APPROVED, seasonId);
        int matches = playtime == null ? 0 : (int) playtime.getMatches();
        long seconds = value(playtime == null ? null : playtime.getSeconds());
        return new LeagueSummary(
                seasonId,
                matches,
                (int) statsRepository.countBySeasonId(seasonId),
                value(totals == null ? null : totals.getKills()),
                value(totals == null ? null : totals.getDeaths()),
                value(totals == null ? null : totals.getAssists()),
                value(totals == null ? null : totals.getCs()),
                value(totals == null ? null : totals.getGold()),
                value(totals == null ? null : totals.getDamage()),
                value(totals == null ? null : totals.getVision()),
                (int) value(totals == null ? null : totals.getPentas()),
                seconds,
                matches == 0 || seconds == 0 ? null : (int) (seconds / matches));
    }

    /** Aggregates over an empty season come back as null, not zero. */
    private static long value(Long sum) {
        return sum == null ? 0L : sum;
    }
}
