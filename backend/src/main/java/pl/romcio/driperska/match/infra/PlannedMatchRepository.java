package pl.romcio.driperska.match.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.match.domain.PlannedMatch;

public interface PlannedMatchRepository extends JpaRepository<PlannedMatch, UUID> {
    List<PlannedMatch> findByStatusOrderByScheduledAtAsc(String status);
}
