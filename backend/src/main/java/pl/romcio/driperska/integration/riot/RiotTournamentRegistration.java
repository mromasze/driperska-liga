package pl.romcio.driperska.integration.riot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "riot_tournament_registration")
public class RiotTournamentRegistration {
    @Id @UuidGenerator private UUID id;
    @Column(name = "key_fingerprint", nullable = false, length = 64) private String keyFingerprint;
    @Column(nullable = false, length = 16) private String platform;
    @Column(name = "callback_url", nullable = false, length = 500) private String callbackUrl;
    @Column(name = "provider_id", nullable = false) private long providerId;
    @Column(name = "tournament_id", nullable = false) private long tournamentId;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected RiotTournamentRegistration() {}
    public RiotTournamentRegistration(String keyFingerprint, String platform, String callbackUrl,
                                      long providerId, long tournamentId) {
        this.keyFingerprint = keyFingerprint;
        this.platform = platform;
        this.callbackUrl = callbackUrl;
        this.providerId = providerId;
        this.tournamentId = tournamentId;
    }
    public long getProviderId() { return providerId; }
    public long getTournamentId() { return tournamentId; }
}

