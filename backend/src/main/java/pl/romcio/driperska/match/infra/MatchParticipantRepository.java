package pl.romcio.driperska.match.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, UUID> {

    @Query("select p from MatchParticipant p where p.playerId = :playerId and p.match.status = :status "
            + "order by p.match.completedAt desc")
    List<MatchParticipant> findByPlayerAndMatchStatus(@Param("playerId") UUID playerId,
                                                      @Param("status") MatchStatus status);
}
