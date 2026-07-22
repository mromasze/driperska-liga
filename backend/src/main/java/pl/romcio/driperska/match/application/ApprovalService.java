package pl.romcio.driperska.match.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.InvalidTransitionException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.match.api.MatchDtos.ApproveRequest;
import pl.romcio.driperska.match.api.MatchDtos.RejectRequest;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchApproval;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchApprovalRepository;

/**
 * The two-eyes sign-off: an admin must consciously confirm (signed checkbox) or send results back
 * for editing — even if the same admin entered them.
 */
@Service
public class ApprovalService {

    private final MatchService matchService;
    private final MatchApprovalRepository approvalRepository;
    private final MatchEventRecorder eventRecorder;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovalService(MatchService matchService,
                           MatchApprovalRepository approvalRepository,
                           MatchEventRecorder eventRecorder,
                           ApplicationEventPublisher eventPublisher) {
        this.matchService = matchService;
        this.approvalRepository = approvalRepository;
        this.eventRecorder = eventRecorder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Match approve(UUID matchId, ApproveRequest req, UUID actor) {
        if (!req.signatureConfirmed()) {
            throw new BusinessRuleException("Wymagane potwierdzenie podpisem");
        }
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.RESULTS_SUBMITTED) {
            throw new InvalidTransitionException("Do zatwierdzenia kwalifikują się tylko wpisane wyniki");
        }
        MatchApproval approval = approval(matchId);
        approval.approve(actor, req.signatureName());
        match.transitionTo(MatchStatus.APPROVED);
        match.setCompletedAt(Instant.now());
        eventRecorder.record(matchId, MatchEventType.APPROVED, actor,
                Map.of("signature", req.signatureName()));
        eventPublisher.publishEvent(new MatchApprovedEvent(matchId));
        return match;
    }

    @Transactional
    public Match reject(UUID matchId, RejectRequest req, UUID actor) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.RESULTS_SUBMITTED) {
            throw new InvalidTransitionException("Do odesłania kwalifikują się tylko wpisane wyniki");
        }
        MatchApproval approval = approval(matchId);
        approval.reject(actor, req.reason());
        match.transitionTo(MatchStatus.REJECTED);
        eventRecorder.record(matchId, MatchEventType.REJECTED, actor, Map.of("reason", req.reason()));
        return match;
    }

    /** Admin-only correction of an already-approved match; triggers a full season recompute. */
    @Transactional
    public Match reopen(UUID matchId, UUID actor) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.APPROVED) {
            throw new InvalidTransitionException("Ponownie otworzyć można tylko zatwierdzony mecz");
        }
        approval(matchId).resubmit(actor);
        match.transitionTo(MatchStatus.RESULTS_SUBMITTED);
        eventRecorder.record(matchId, MatchEventType.REOPENED, actor, null);
        eventPublisher.publishEvent(new RankingRecalculationEvent(match.getSeasonId()));
        return match;
    }

    private MatchApproval approval(UUID matchId) {
        return approvalRepository.findByMatchId(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Brak rekordu akceptacji dla meczu " + matchId));
    }
}
