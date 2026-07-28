package pl.romcio.driperska.ranking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.romcio.driperska.ranking.domain.ScoringConfig;
import pl.romcio.driperska.season.domain.Season;
import pl.romcio.driperska.season.infra.SeasonRepository;

class ScoringConfigProviderTest {

    @Test
    void olderSeasonConfigReceivesDefaultsForNewScoringFields() throws Exception {
        UUID seasonId = UUID.randomUUID();
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode oldConfig = objectMapper.valueToTree(ScoringConfig.defaults());
        oldConfig.remove("lpBestKdaBonus");
        oldConfig.remove("lpPerfectKdaBonus");
        oldConfig.remove("rankingActivityPointsPerGame");
        oldConfig.remove("rankingActivityMaxGames");
        oldConfig.put("lpWin", 12);

        Season season = mock(Season.class);
        when(season.getScoringConfigJson()).thenReturn(objectMapper.writeValueAsString(oldConfig));
        SeasonRepository repository = mock(SeasonRepository.class);
        when(repository.findById(seasonId)).thenReturn(Optional.of(season));

        ScoringConfig config = new ScoringConfigProvider(repository, objectMapper)
                .forSeason(seasonId);
        ScoringConfig defaults = ScoringConfig.defaults();

        assertThat(config.lpWin()).isEqualTo(12);
        assertThat(config.lpBestKdaBonus()).isEqualTo(defaults.lpBestKdaBonus());
        assertThat(config.lpPerfectKdaBonus()).isEqualTo(defaults.lpPerfectKdaBonus());
        assertThat(config.rankingActivityPointsPerGame())
                .isEqualTo(defaults.rankingActivityPointsPerGame());
        assertThat(config.rankingActivityMaxGames())
                .isEqualTo(defaults.rankingActivityMaxGames());
    }
}
