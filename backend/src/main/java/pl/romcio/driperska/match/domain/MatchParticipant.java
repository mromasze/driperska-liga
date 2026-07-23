package pl.romcio.driperska.match.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;

@Entity
@Table(name = "match_participant")
public class MatchParticipant {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "champion_id")
    private Integer championId;

    // Entered stats
    private int kills;
    private int deaths;
    private int assists;
    private int cs;
    private int gold;
    @Column(name = "damage_to_champions")
    private int damageToChampions;
    @Column(name = "vision_score")
    private int visionScore;
    @Column(name = "largest_multi_kill")
    private int largestMultiKill;

    // Computed on approval
    @Column(name = "performance_rating")
    private Double performanceRating;
    @Column(name = "lp_awarded")
    private Integer lpAwarded;
    @Column(name = "mmr_delta")
    private Double mmrDelta;
    @Column(name = "is_mvp")
    private boolean mvp;

    protected MatchParticipant() {
    }

    public MatchParticipant(UUID playerId, Side side, Role role) {
        this.playerId = playerId;
        this.side = side;
        this.role = role;
    }

    void assignMatch(Match match) {
        this.match = match;
    }

    public double kda() {
        return (kills + assists) / (double) Math.max(1, deaths);
    }

    public void applyStats(Integer championId, int kills, int deaths, int assists, int cs,
                           int gold, int damageToChampions, int visionScore, int largestMultiKill) {
        this.championId = championId;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.cs = cs;
        this.gold = gold;
        this.damageToChampions = damageToChampions;
        this.visionScore = visionScore;
        this.largestMultiKill = largestMultiKill;
    }

    public void applyComputed(double performanceRating, int lpAwarded, double mmrDelta, boolean mvp) {
        this.performanceRating = performanceRating;
        this.lpAwarded = lpAwarded;
        this.mmrDelta = mmrDelta;
        this.mvp = mvp;
    }

    public UUID getId() {
        return id;
    }

    public Match getMatch() {
        return match;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Integer getChampionId() {
        return championId;
    }

    public void setChampionId(Integer championId) {
        this.championId = championId;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getAssists() {
        return assists;
    }

    public int getCs() {
        return cs;
    }

    public int getGold() {
        return gold;
    }

    public int getDamageToChampions() {
        return damageToChampions;
    }

    public int getVisionScore() {
        return visionScore;
    }

    public int getLargestMultiKill() {
        return largestMultiKill;
    }

    public Double getPerformanceRating() {
        return performanceRating;
    }

    public Integer getLpAwarded() {
        return lpAwarded;
    }

    public Double getMmrDelta() {
        return mmrDelta;
    }

    public boolean isMvp() {
        return mvp;
    }
}
