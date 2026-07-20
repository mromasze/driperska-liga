package pl.romcio.driperska.match.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchStatus;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    @EntityGraph(attributePaths = "participants")
    Page<Match> findByStatus(MatchStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "participants")
    Page<Match> findByStatusAndSeasonId(MatchStatus status, UUID seasonId, Pageable pageable);

    @EntityGraph(attributePaths = "participants")
    Page<Match> findBySeasonId(UUID seasonId, Pageable pageable);

    @EntityGraph(attributePaths = "participants")
    List<Match> findByStatusOrderByCompletedAtDesc(MatchStatus status);

    long countByStatus(MatchStatus status);
}
