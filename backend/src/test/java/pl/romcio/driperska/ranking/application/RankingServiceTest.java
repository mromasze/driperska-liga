package pl.romcio.driperska.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.romcio.driperska.ranking.domain.PlayerSeasonStats;
import pl.romcio.driperska.ranking.domain.ScoringConfig;
import pl.romcio.driperska.ranking.infra.PlayerSeasonStatsRepository;

class RankingServiceTest {

    @Test
    void activityBonusRewardsGamesAndStopsAtConfiguredCap() {
        UUID seasonId = UUID.randomUUID();
        PlayerSeasonStatsRepository repository = mock(PlayerSeasonStatsRepository.class);
        ScoringConfigProvider configProvider = mock(ScoringConfigProvider.class);
        ScoringConfig config = ScoringConfig.defaults();
        PlayerSeasonStats stats = new PlayerSeasonStats(
                UUID.randomUUID(), seasonId, config.mmrStart());
        for (int game = 0; game < 25; game++) {
            stats.addMatch(true, 7, 50.0, 0.0, false, false, false);
        }
        when(repository.findBySeasonId(seasonId)).thenReturn(List.of(stats));
        when(configProvider.forSeason(seasonId)).thenReturn(config);

        RankingService service = new RankingService(
                null, repository, null, null, null, configProvider, null);

        RankingService.RankingEntry entry = service.ranking(seasonId).getFirst();

        assertThat(entry.baseScore()).isEqualTo(7.0);
        assertThat(entry.activityBonus()).isEqualTo(
                config.rankingActivityMaxGames() * config.rankingActivityPointsPerGame());
        assertThat(entry.rankingScore()).isEqualTo(
                entry.baseScore() + entry.activityBonus());
    }
}
