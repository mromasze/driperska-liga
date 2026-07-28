package pl.romcio.driperska.ranking.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import pl.romcio.driperska.ranking.domain.PlayerSeasonStats;

@DataJpaTest
class PlayerSeasonStatsRepositoryIT {

    @Autowired
    PlayerSeasonStatsRepository repository;

    @Test
    void replacesSamePlayerSeasonStatsWithoutUniqueConstraintConflict() {
        UUID playerId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        PlayerSeasonStats oldStats = new PlayerSeasonStats(playerId, seasonId, 1000.0);
        repository.saveAndFlush(oldStats);

        assertEquals(1, repository.deleteBySeasonId(seasonId));

        PlayerSeasonStats rebuiltStats = new PlayerSeasonStats(playerId, seasonId, 1000.0);
        repository.saveAndFlush(rebuiltStats);

        var rows = repository.findBySeasonId(seasonId);
        assertEquals(1, rows.size());
        assertEquals(rebuiltStats.getId(), rows.getFirst().getId());
    }
}
