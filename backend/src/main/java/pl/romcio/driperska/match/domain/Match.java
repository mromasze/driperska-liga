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
    @Column(name = "teams_drawn_at") private Instant teamsDrawnAt;
    @Column(name = "riot_tournament_code", unique = true) private String riotTournamentCode;
    @Column(name = "riot_game_id") private String riotGameId;
    @Column(name = "riot_match_id") private String riotMatchId;
    @Column(name = "riot_metadata_token", length = 64) private String riotMetadataToken;
    @Column(name = "riot_lobby_created_at") private Instant riotLobbyCreatedAt;
    @Column(name = "riot_callback_received_at") private Instant riotCallbackReceivedAt;
    @Column(name = "riot_results_imported_at") private Instant riotResultsImportedAt;
    @Column(name = "riot_import_error", columnDefinition = "text") private String riotImportError;

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
    public void markRiotLobby(String tournamentCode, String metadataToken) {
        this.riotTournamentCode = tournamentCode;
        this.riotMetadataToken = metadataToken;
        this.riotLobbyCreatedAt = Instant.now();
        this.riotImportError = null;
    }
    public void clearRiotLobby() {
        this.riotTournamentCode = null;
        this.riotGameId = null;
        this.riotMatchId = null;
        this.riotMetadataToken = null;
        this.riotLobbyCreatedAt = null;
        this.riotCallbackReceivedAt = null;
        this.riotResultsImportedAt = null;
        this.riotImportError = null;
    }
    public void markRiotCallback(String gameId, String matchId) {
        this.riotGameId = gameId;
        this.riotMatchId = matchId;
        this.riotCallbackReceivedAt = Instant.now();
    }
    public void markRiotImportSuccess(String matchId) {
        this.riotMatchId = matchId;
        this.riotResultsImportedAt = Instant.now();
        this.riotImportError = null;
    }
    public void markRiotImportFailure(String message) {
        String detail = message == null ? "Nieznany błąd importu Riot" : message;
        this.riotImportError = detail.substring(0, Math.min(detail.length(), 2000));
    }
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
    public Instant getTeamsDrawnAt() { return teamsDrawnAt; }
    public void setTeamsDrawnAt(Instant teamsDrawnAt) { this.teamsDrawnAt = teamsDrawnAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public List<MatchParticipant> getParticipants() { return participants; }
    public List<UUID> getPoolPlayerIds() { return poolPlayerIds; }
    public void setPoolPlayerIds(List<UUID> poolPlayerIds) { this.poolPlayerIds = new ArrayList<>(poolPlayerIds); }
    public String getRiotTournamentCode() { return riotTournamentCode; }
    public String getRiotGameId() { return riotGameId; }
    public String getRiotMatchId() { return riotMatchId; }
    public String getRiotMetadataToken() { return riotMetadataToken; }
    public Instant getRiotLobbyCreatedAt() { return riotLobbyCreatedAt; }
    public Instant getRiotCallbackReceivedAt() { return riotCallbackReceivedAt; }
    public Instant getRiotResultsImportedAt() { return riotResultsImportedAt; }
    public String getRiotImportError() { return riotImportError; }
}