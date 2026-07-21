package pl.romcio.driperska.match.infra;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> findForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "participants")
    @Query("select distinct m from Match m join m.poolPlayerIds playerId "
            + "where playerId = :playerId and m.status = :status order by m.createdAt desc")
    List<Match> findForPlayerAndStatus(@Param("playerId") UUID playerId,
                                      @Param("status") MatchStatus status,
                                      Pageable pageable);
    long countByStatus(MatchStatus status);
}