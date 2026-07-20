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

/** The two-eyes sign-off record for a match's results. */
@Entity
@Table(name = "match_approval")
public class MatchApproval {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "match_id", nullable = false, unique = true)
    private UUID matchId;

    @Column(name = "submitted_by", nullable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalDecision decision = ApprovalDecision.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "signature_confirmed", nullable = false)
    private boolean signatureConfirmed;

    @Column(name = "signature_name")
    private String signatureName;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    protected MatchApproval() {
    }

    public MatchApproval(UUID matchId, UUID submittedBy) {
        this.matchId = matchId;
        this.submittedBy = submittedBy;
    }

    public void resubmit(UUID submittedBy) {
        this.submittedBy = submittedBy;
        this.submittedAt = Instant.now();
        this.decision = ApprovalDecision.PENDING;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.rejectionReason = null;
        this.signatureConfirmed = false;
        this.signatureName = null;
    }

    public void approve(UUID reviewedBy, String signatureName) {
        this.decision = ApprovalDecision.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = Instant.now();
        this.signatureConfirmed = true;
        this.signatureName = signatureName;
    }

    public void reject(UUID reviewedBy, String reason) {
        this.decision = ApprovalDecision.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = Instant.now();
        this.rejectionReason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public boolean isSignatureConfirmed() {
        return signatureConfirmed;
    }

    public String getSignatureName() {
        return signatureName;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
