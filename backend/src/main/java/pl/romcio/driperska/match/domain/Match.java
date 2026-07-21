package pl.romcio.driperska.match.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.InvalidTransitionException;

@Entity
@Table(name = "match_game")
public class Match {
    @Id @UuidGenerator private UUID id;
    @Column(name = "season_id", nullable = false) private UUID seasonId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MatchStatus status = MatchStatus.DRAFT;
    @Enumerated(EnumType.STRING) @Column(name = "draw_mode", nullable = false) private DrawMode drawMode = DrawMode.BALANCED;
    @Column(name = "draw_round", nullable = false) private int drawRound;
    @Enumerated(EnumType.STRING) @Column(name = "winning_side") private Side winningSide;
    @Column(name = "duration_seconds") private Integer durationSeconds;
    private String patch;
    @Column(columnDefinition = "text") private String notes;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;

    @ElementCollection
    @CollectionTable(name = "match_pool", joinColumns = @JoinColumn(name = "match_id"))
    @Column(name = "player_id")
    private List<UUID> poolPlayerIds = new ArrayList<>();

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("side ASC, role ASC")
    private List<MatchParticipant> participants = new ArrayList<>();

    protected Match() {}
    public Match(UUID seasonId, DrawMode drawMode, UUID createdBy) {
        this.seasonId = seasonId;
        this.drawMode = drawMode;
        this.createdBy = createdBy;
    }

    public void transitionTo(MatchStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException("Niedozwolone przejście meczu: %s → %s".formatted(status, target));
        }
        status = target;
    }

    public void replaceParticipants(List<MatchParticipant> replacements) {
        participants.clear();
        replacements.forEach(p -> {
            p.assignMatch(this);
            participants.add(p);
        });
    }

    public void advanceDrawRound() { drawRound++; }
    public UUID getId() { return id; }
    public UUID getSeasonId() { return seasonId; }
    public MatchStatus getStatus() { return status; }
    public DrawMode getDrawMode() { return drawMode; }
    public void setDrawMode(DrawMode drawMode) { this.drawMode = drawMode; }
    public int getDrawRound() { return drawRound; }
    public Side getWinningSide() { return winningSide; }
    public void setWinningSide(Side winningSide) { this.winningSide = winningSide; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getPatch() { return patch; }
    public void setPatch(String patch) { this.patch = patch; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public List<MatchParticipant> getParticipants() { return participants; }
    public List<UUID> getPoolPlayerIds() { return poolPlayerIds; }
    public void setPoolPlayerIds(List<UUID> poolPlayerIds) { this.poolPlayerIds = new ArrayList<>(poolPlayerIds); }
}