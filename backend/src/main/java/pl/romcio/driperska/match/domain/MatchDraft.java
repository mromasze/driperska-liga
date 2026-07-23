package pl.romcio.driperska.match.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistent envelope for one match's champion draft. The actual pick/ban/swap state lives in the
 * JSON `state` column (see DraftState); `deadline` is mirrored as a real column so the timeout
 * scheduler can query due steps cheaply.
 */
@Entity
@Table(name = "match_draft")
public class MatchDraft {

    @Id
    @Column(name = "match_id")
    private UUID matchId;

    @Column(name = "state", nullable = false, columnDefinition = "text")
    private String state;

    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MatchDraft() {}

    public MatchDraft(UUID matchId, String state, Instant deadline) {
        this.matchId = matchId;
        this.state = state;
        this.deadline = deadline;
        this.updatedAt = Instant.now();
    }

    public UUID getMatchId() { return matchId; }

    public String getState() { return state; }

    public Instant getDeadline() { return deadline; }

    public void update(String state, Instant deadline) {
        this.state = state;
        this.deadline = deadline;
        this.updatedAt = Instant.now();
    }
}
