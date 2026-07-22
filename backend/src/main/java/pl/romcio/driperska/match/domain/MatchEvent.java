package pl.romcio.driperska.match.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Append-only audit entry for a match lifecycle transition. */
@Entity
@Table(name = "match_event")
public class MatchEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchEventType type;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MatchEvent() {
    }

    public MatchEvent(UUID matchId, MatchEventType type, UUID actorAccountId, String payloadJson) {
        this.matchId = matchId;
        this.type = type;
        this.actorAccountId = actorAccountId;
        this.payloadJson = payloadJson;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public MatchEventType getType() {
        return type;
    }

    public UUID getActorAccountId() {
        return actorAccountId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
