package pl.romcio.driperska.match.infra;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.match.domain.MatchApproval;

public interface MatchApprovalRepository extends JpaRepository<MatchApproval, UUID> {

    Optional<MatchApproval> findByMatchId(UUID matchId);

    void deleteByMatchId(UUID matchId);
}
