package pl.romcio.driperska.match.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.match.domain.MatchEvent;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {

    List<MatchEvent> findByMatchIdOrderByCreatedAtAsc(UUID matchId);

    /** match_event carries no FK to match_game, so a deleted match must take its trail with it. */
    void deleteByMatchId(UUID matchId);
}
