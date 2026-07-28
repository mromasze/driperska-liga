package pl.romcio.driperska.ranking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Materialised per-player, per-season aggregate powering the ranking table. */
@Entity
@Table(name = "player_season_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"player_id", "season_id"}))
public class PlayerSeasonStats {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Column(name = "season_id", nullable = false)
    private UUID seasonId;

    @Column(name = "total_lp", nullable = false)
    private int totalLp;

    @Column(nullable = false)
    private int games;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    /** Running sum of PR, used to derive the average without re-reading history. */
    @Column(name = "sum_pr", nullable = false)
    private double sumPr;

    @Column(name = "mmr", nullable = false)
    private double mmr;

    @Column(name = "mvp_count", nullable = false)
    private int mvpCount;

    @Column(name = "ace_count", nullable = false)
    private int aceCount;

    @Column(name = "penta_count", nullable = false)
    private int pentaCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PlayerSeasonStats() {
    }

    public PlayerSeasonStats(UUID playerId, UUID seasonId, double startMmr) {
        this.playerId = playerId;
        this.seasonId = seasonId;
        this.mmr = startMmr;
    }

    public void addMatch(boolean won, int lp, double pr, double mmrDelta,
                         boolean mvp, boolean ace, boolean penta) {
        this.games++;
        if (won) {
            this.wins++;
        } else {
            this.losses++;
        }
        this.totalLp += lp;
        this.sumPr += pr;
        this.mmr += mmrDelta;
        if (mvp) {
            this.mvpCount++;
        }
        if (ace) {
            this.aceCount++;
        }
        if (penta) {
            this.pentaCount++;
        }
        this.updatedAt = Instant.now();
    }

    public double avgPerformanceRating() {
        return games == 0 ? 0 : Math.round(sumPr / games * 100.0) / 100.0;
    }

    public double winRate() {
        return games == 0 ? 0 : Math.round((double) wins / games * 1000.0) / 1000.0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getSeasonId() {
        return seasonId;
    }

    public int getTotalLp() {
        return totalLp;
    }

    public int getGames() {
        return games;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public double getMmr() {
        return Math.round(mmr * 100.0) / 100.0;
    }

    public int getMvpCount() {
        return mvpCount;
    }

    public int getAceCount() {
        return aceCount;
    }

    public int getPentaCount() {
        return pentaCount;
    }
}
