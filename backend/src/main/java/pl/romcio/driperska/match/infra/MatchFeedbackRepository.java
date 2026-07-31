package pl.romcio.driperska.match.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.romcio.driperska.match.domain.MatchFeedback;

public interface MatchFeedbackRepository extends JpaRepository<MatchFeedback, UUID> {
    Optional<MatchFeedback> findByMatchIdAndVoterPlayerId(UUID matchId, UUID voterPlayerId);
    List<MatchFeedback> findByMatchId(UUID matchId);
    List<MatchFeedback> findByVoterPlayerId(UUID voterPlayerId);

    /**
     * Newest praise with something written in it — the source of the public opinion ticker. Only rows
     * naming an upvoted player, because that is the person the note is being said about.
     */
    @Query("select f from MatchFeedback f where f.note is not null and f.upvotePlayerId is not null "
            + "order by f.updatedAt desc")
    List<MatchFeedback> findRecentPraise(Pageable pageable);

    void deleteByMatchId(UUID matchId);
}
