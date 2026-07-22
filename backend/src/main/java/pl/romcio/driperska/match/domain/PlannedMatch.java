package pl.romcio.driperska.match.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** A tentative, scheduled match players can RSVP to. Not a real {@link Match} until it actually happens. */
@Entity
@Table(name = "planned_match")
public class PlannedMatch {

    public static final String PLANNED = "PLANNED";
    public static final String CANCELLED = "CANCELLED";

    @Id @UuidGenerator private UUID id;
    @Column(name = "scheduled_at", nullable = false) private Instant scheduledAt;
    @Column(columnDefinition = "text") private String note;
    @Column(nullable = false, length = 16) private String status = PLANNED;
    @Column(name = "created_by", nullable = false) private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "planned_match_rsvp", joinColumns = @JoinColumn(name = "planned_match_id"))
    private List<Rsvp> rsvps = new ArrayList<>();

    protected PlannedMatch() {}

    public PlannedMatch(Instant scheduledAt, String note, UUID createdBy) {
        this.scheduledAt = scheduledAt;
        this.note = note;
        this.createdBy = createdBy;
    }

    public void setResponse(UUID playerId, String response) {
        for (Rsvp r : rsvps) {
            if (r.getPlayerId().equals(playerId)) { r.update(response); return; }
        }
        rsvps.add(new Rsvp(playerId, response));
    }

    public void cancel() { this.status = CANCELLED; }

    public UUID getId() { return id; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getNote() { return note; }
    public String getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Rsvp> getRsvps() { return rsvps; }

    @Embeddable
    public static class Rsvp {
        @Column(name = "player_id", nullable = false) private UUID playerId;
        @Column(nullable = false, length = 8) private String response;
        @Column(name = "responded_at", nullable = false) private Instant respondedAt = Instant.now();

        protected Rsvp() {}
        Rsvp(UUID playerId, String response) { this.playerId = playerId; this.response = response; }
        void update(String response) { this.response = response; this.respondedAt = Instant.now(); }

        public UUID getPlayerId() { return playerId; }
        public String getResponse() { return response; }
        public Instant getRespondedAt() { return respondedAt; }
    }
}
