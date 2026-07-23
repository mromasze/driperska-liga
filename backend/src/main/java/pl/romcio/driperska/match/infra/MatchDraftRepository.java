package pl.romcio.driperska.match.infra;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.match.domain.MatchDraft;

public interface MatchDraftRepository extends JpaRepository<MatchDraft, UUID> {

    /** Drafts whose current step has run past its deadline — used by the timeout scheduler. */
    @Query("select d.matchId from MatchDraft d where d.deadline is not null and d.deadline < :now")
    List<UUID> findMatchIdsPastDeadline(@Param("now") Instant now);
}
