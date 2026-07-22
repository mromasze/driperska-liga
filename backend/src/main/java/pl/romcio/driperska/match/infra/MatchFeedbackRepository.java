package pl.romcio.driperska.match.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.match.domain.MatchFeedback;

public interface MatchFeedbackRepository extends JpaRepository<MatchFeedback, UUID> {
    Optional<MatchFeedback> findByMatchIdAndVoterPlayerId(UUID matchId, UUID voterPlayerId);
    List<MatchFeedback> findByMatchId(UUID matchId);
    List<MatchFeedback> findByVoterPlayerId(UUID voterPlayerId);
}
