package pl.romcio.driperska.match.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.match.domain.MatchEvent;

public interface MatchEventRepository extends JpaRepository<MatchEvent, UUID> {

    List<MatchEvent> findByMatchIdOrderByCreatedAtAsc(UUID matchId);
}
