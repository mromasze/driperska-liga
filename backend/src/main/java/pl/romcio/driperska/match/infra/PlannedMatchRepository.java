package pl.romcio.driperska.match.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.match.domain.PlannedMatch;

public interface PlannedMatchRepository extends JpaRepository<PlannedMatch, UUID> {
    List<PlannedMatch> findByStatusOrderByScheduledAtAsc(String status);

    /** Only matches still ahead of us — what attendance can sensibly be confirmed for. */
    List<PlannedMatch> findByStatusAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(
            String status, Instant from);
}
