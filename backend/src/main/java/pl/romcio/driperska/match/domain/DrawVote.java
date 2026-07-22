package pl.romcio.driperska.match.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "match_draw_vote", uniqueConstraints = @UniqueConstraint(
        name = "uk_match_draw_vote_round_player",
        columnNames = {"match_id", "draw_round", "player_id"}))
public class DrawVote {
    @Id @UuidGenerator private UUID id;
    @Column(name = "match_id", nullable = false) private UUID matchId;
    @Column(name = "draw_round", nullable = false) private int drawRound;
    @Column(name = "player_id", nullable = false) private UUID playerId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DrawVoteDecision decision;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected DrawVote() {}
    public DrawVote(UUID matchId, int drawRound, UUID playerId, UUID accountId, DrawVoteDecision decision) {
        this.matchId = matchId;
        this.drawRound = drawRound;
        this.playerId = playerId;
        this.accountId = accountId;
        this.decision = decision;
    }
    public UUID getPlayerId() { return playerId; }
    public DrawVoteDecision getDecision() { return decision; }
}