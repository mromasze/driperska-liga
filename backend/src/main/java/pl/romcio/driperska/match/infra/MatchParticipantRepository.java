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
            + "order by coalesce(p.match.startedAt, p.match.completedAt) desc")
    List<MatchParticipant> findByPlayerAndMatchStatus(@Param("playerId") UUID playerId,
                                                      @Param("status") MatchStatus status);

    /**
     * Season-wide totals for the landing page. Sums are {@code null} when the season has no scored
     * matches yet — the caller substitutes zero.
     */
    interface SeasonTotals {
        Long getKills();
        Long getDeaths();
        Long getAssists();
        Long getCs();
        Long getGold();
        Long getDamage();
        Long getVision();
        Long getPentas();
        Long getEntries();
    }

    @Query("""
            select sum(p.kills) as kills, sum(p.deaths) as deaths, sum(p.assists) as assists,
                   sum(p.cs) as cs, sum(p.gold) as gold, sum(p.damageToChampions) as damage,
                   sum(p.visionScore) as vision,
                   sum(case when p.largestMultiKill >= 5 then 1 else 0 end) as pentas,
                   count(p.id) as entries
            from MatchParticipant p
            where p.match.status = :status and p.match.seasonId = :seasonId
            """)
    SeasonTotals totalsForSeason(@Param("status") MatchStatus status, @Param("seasonId") UUID seasonId);
}
