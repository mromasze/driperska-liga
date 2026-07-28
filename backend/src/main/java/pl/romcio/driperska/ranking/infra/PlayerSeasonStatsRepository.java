package pl.romcio.driperska.ranking.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.romcio.driperska.ranking.domain.PlayerSeasonStats;

public interface PlayerSeasonStatsRepository extends JpaRepository<PlayerSeasonStats, UUID> {

    Optional<PlayerSeasonStats> findByPlayerIdAndSeasonId(UUID playerId, UUID seasonId);

    List<PlayerSeasonStats> findBySeasonId(UUID seasonId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PlayerSeasonStats stats where stats.seasonId = :seasonId")
    int deleteBySeasonId(@Param("seasonId") UUID seasonId);
}
