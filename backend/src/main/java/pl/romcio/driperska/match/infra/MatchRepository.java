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

    /**
     * List ordering for every paginated match listing: by the actual game start, falling back to
     * creation time for matches that have not started. Expressed as JPQL (not a {@link Sort} with
     * {@code NULLS LAST}) on purpose — Spring Data runs derived queries through the Criteria API,
     * and Hibernate 6 rejects null precedence there with
     * {@code UnsupportedOperationException: Applying Null Precedence using Criteria Queries is not
     * yet supported}, which turned every GET /matches into a 500.
     */
    String LIST_ORDER = " order by coalesce(m.startedAt, m.createdAt) desc, m.createdAt desc";

    @EntityGraph(attributePaths = "participants")
    @Query("select m from Match m" + LIST_ORDER)
    Page<Match> findAllForListing(Pageable pageable);

    @EntityGraph(attributePaths = "participants")
    @Query("select m from Match m where m.status = :status" + LIST_ORDER)
    Page<Match> findByStatus(@Param("status") MatchStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "participants")
    @Query("select m from Match m where m.status = :status and m.seasonId = :seasonId" + LIST_ORDER)
    Page<Match> findByStatusAndSeasonId(@Param("status") MatchStatus status,
                                        @Param("seasonId") UUID seasonId, Pageable pageable);

    @EntityGraph(attributePaths = "participants")
    @Query("select m from Match m where m.seasonId = :seasonId" + LIST_ORDER)
    Page<Match> findBySeasonId(@Param("seasonId") UUID seasonId, Pageable pageable);

    @EntityGraph(attributePaths = "participants")
    List<Match> findByStatusOrderByCompletedAtDesc(MatchStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> findForUpdate(@Param("id") UUID id);

    Optional<Match> findByRiotTournamentCode(String riotTournamentCode);

    @EntityGraph(attributePaths = "participants")
    Optional<Match> findDetailedById(UUID id);

    @EntityGraph(attributePaths = "participants")
    @Query("select m from Match m "
            + "where :playerId member of m.poolPlayerIds and m.status = :status "
            + "order by coalesce(m.startedAt, m.createdAt) desc")
    List<Match> findForPlayerAndStatus(@Param("playerId") UUID playerId,
                                      @Param("status") MatchStatus status,
                                      Pageable pageable);
    @EntityGraph(attributePaths = "participants")
    @Query("select m from Match m "
            + "where :playerId member of m.poolPlayerIds and m.status in :statuses "
            + "order by coalesce(m.startedAt, m.createdAt) desc")
    List<Match> findForPlayerAndStatuses(@Param("playerId") UUID playerId,
                                        @Param("statuses") java.util.Collection<MatchStatus> statuses,
                                        Pageable pageable);
    long countByStatus(MatchStatus status);

    @Query("select m.id from Match m where m.status = :status and m.teamsDrawnAt is not null "
            + "and m.teamsDrawnAt < :threshold")
    List<UUID> findIdsForAutoConfirm(@Param("status") MatchStatus status,
                                     @Param("threshold") java.time.Instant threshold);
}