package pl.romcio.driperska.match.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** One participant's optional post-match rating: one upvote, one downvote and a note. */
@Entity
@Table(name = "match_feedback")
public class MatchFeedback {

    @Id @UuidGenerator private UUID id;
    @Column(name = "match_id", nullable = false) private UUID matchId;
    @Column(name = "voter_player_id", nullable = false) private UUID voterPlayerId;
    @Column(name = "upvote_player_id") private UUID upvotePlayerId;
    @Column(name = "downvote_player_id") private UUID downvotePlayerId;
    @Column(columnDefinition = "text") private String note;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected MatchFeedback() {}

    public MatchFeedback(UUID matchId, UUID voterPlayerId) {
        this.matchId = matchId;
        this.voterPlayerId = voterPlayerId;
    }

    public void update(UUID upvotePlayerId, UUID downvotePlayerId, String note) {
        this.upvotePlayerId = upvotePlayerId;
        this.downvotePlayerId = downvotePlayerId;
        this.note = note;
        this.updatedAt = Instant.now();
    }

    public UUID getMatchId() { return matchId; }
    public UUID getVoterPlayerId() { return voterPlayerId; }
    public UUID getUpvotePlayerId() { return upvotePlayerId; }
    public UUID getDownvotePlayerId() { return downvotePlayerId; }
    public String getNote() { return note; }
}
