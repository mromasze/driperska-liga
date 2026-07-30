package pl.romcio.driperska.match.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.match.api.MatchDtos.ManualSlotRequest;
import pl.romcio.driperska.match.application.MatchSubmissionService.Submission;
import pl.romcio.driperska.match.domain.ApprovalDecision;
import pl.romcio.driperska.match.domain.MatchStatus;

/** Payloads of the moderator panel: recording a played match and tracking its approval. */
public final class ModerationDtos {

    private ModerationDtos() {
    }

    public record CreateSubmissionRequest(
            /** Defaults to the active season when omitted. */
            UUID seasonId,
            /** When the game was actually played — drives the ordering of every match listing. */
            @NotNull Instant playedAt,
            @NotNull @Size(min = 10, max = 10, message = "Skład musi mieć dokładnie 10 graczy")
            @Valid List<ManualSlotRequest> teams) {
    }

    /** Both fields are optional: send the date, the roster, or both. */
    public record UpdateSubmissionRequest(
            Instant playedAt,
            @Size(min = 10, max = 10, message = "Skład musi mieć dokładnie 10 graczy")
            @Valid List<ManualSlotRequest> teams) {
    }

    /**
     * One row of "my submissions". {@code status} tells the moderator what to do next: {@code LIVE}
     * = statistics still missing, {@code RESULTS_SUBMITTED} = waiting for the admin (still editable),
     * {@code REJECTED} = sent back with a reason, {@code APPROVED} = frozen and counted in the
     * ranking.
     */
    public record SubmissionResponse(
            UUID id,
            UUID seasonId,
            MatchStatus status,
            Instant playedAt,
            Instant createdAt,
            Side winningSide,
            Integer durationSeconds,
            int participantCount,
            /** True once every participant has a champion + statistics, i.e. the form is complete. */
            boolean statsEntered,
            ApprovalDecision decision,
            Instant submittedAt,
            Instant reviewedAt,
            String rejectionReason) {

        public static SubmissionResponse from(Submission submission) {
            var match = submission.match();
            var approval = submission.approval();
            boolean stats = !match.getParticipants().isEmpty()
                    && match.getParticipants().stream().allMatch(p -> p.getChampionId() != null);
            return new SubmissionResponse(
                    match.getId(), match.getSeasonId(), match.getStatus(),
                    match.getStartedAt(), match.getCreatedAt(), match.getWinningSide(),
                    match.getDurationSeconds(), match.getParticipants().size(), stats,
                    approval == null ? null : approval.getDecision(),
                    approval == null ? null : approval.getSubmittedAt(),
                    approval == null ? null : approval.getReviewedAt(),
                    approval == null ? null : approval.getRejectionReason());
        }
    }
}
