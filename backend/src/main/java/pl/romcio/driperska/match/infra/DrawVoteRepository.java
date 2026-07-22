package pl.romcio.driperska.match.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.match.domain.DrawVote;

public interface DrawVoteRepository extends JpaRepository<DrawVote, UUID> {
    boolean existsByMatchIdAndDrawRoundAndPlayerId(UUID matchId, int drawRound, UUID playerId);
    List<DrawVote> findByMatchIdAndDrawRound(UUID matchId, int drawRound);
}