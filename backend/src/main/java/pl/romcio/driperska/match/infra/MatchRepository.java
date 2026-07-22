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
    @Override
    @EntityGraph(attributePaths = "participants")
    Page<Match> findAll(Pageable pageable);

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

    Optional<Match> findByRiotTournamentCode(String riotTournamentCode);

    @EntityGraph(attributePaths = "participants")
    Optional<Match> findDetailedById(UUID id);

    @EntityGraph(attributePaths = "participants")
    @Query("select distinct m from Match m join m.poolPlayerIds playerId "
            + "where playerId = :playerId and m.status = :status order by m.createdAt desc")
    List<Match> findForPlayerAndStatus(@Param("playerId") UUID playerId,
                                      @Param("status") MatchStatus status,
                                      Pageable pageable);
    @EntityGraph(attributePaths = "participants")
    @Query("select distinct m from Match m join m.poolPlayerIds playerId "
            + "where playerId = :playerId and m.status in :statuses order by m.createdAt desc")
    List<Match> findForPlayerAndStatuses(@Param("playerId") UUID playerId,
                                        @Param("statuses") java.util.Collection<MatchStatus> statuses,
                                        Pageable pageable);
    long countByStatus(MatchStatus status);

    @Query("select m.id from Match m where m.status = :status and m.teamsDrawnAt is not null "
            + "and m.teamsDrawnAt < :threshold")
    List<UUID> findIdsForAutoConfirm(@Param("status") MatchStatus status,
                                     @Param("threshold") java.time.Instant threshold);
}