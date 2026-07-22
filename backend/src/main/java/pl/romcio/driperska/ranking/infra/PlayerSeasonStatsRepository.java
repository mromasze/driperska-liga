package pl.romcio.driperska.ranking.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.ranking.domain.PlayerSeasonStats;

public interface PlayerSeasonStatsRepository extends JpaRepository<PlayerSeasonStats, UUID> {

    Optional<PlayerSeasonStats> findByPlayerIdAndSeasonId(UUID playerId, UUID seasonId);

    List<PlayerSeasonStats> findBySeasonId(UUID seasonId);

    void deleteBySeasonId(UUID seasonId);
}
