package pl.romcio.driperska.match.application;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.*;

/**
 * Bulk housekeeping an admin can run when the match list has filled up with abandoned evenings:
 * drafts nobody finished, matches still "running" days later, half-entered results.
 *
 * <p>Two different operations, deliberately kept apart:
 *
 * <ul>
 *   <li><b>Stopping</b> ({@link #stopAllRunning}) cancels — the match stays in the list as
 *       CANCELLED, with its history intact. Reversible in the sense that nothing is lost.</li>
 *   <li><b>Deleting</b> ({@link #deleteDraftsInProgress}, {@link #purgeUnapproved},
 *       {@link #delete}) removes the row and everything hanging off it. <b>Irreversible.</b></li>
 * </ul>
 *
 * <p>Deletion has to sweep by hand. Only {@code match_draft} has a real
 * {@code ON DELETE CASCADE} back to {@code match_game}; {@code match_event},
 * {@code match_approval}, {@code match_draw_vote} and {@code match_feedback} merely hold a
 * {@code match_id} column, so dropping a match without them would leave orphan rows behind
 * (participants and the player pool are cascaded by JPA from {@link Match} itself).
 *
 * <p>APPROVED matches are never touched by the bulk operations: they are the league's record and
 * every player's LP, MMR and season stats are derived from them.
 */
@Service
public class MatchMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MatchMaintenanceService.class);

    /**
     * Statuses that mean "this evening is still going on somewhere". RESULTS_SUBMITTED is excluded —
     * the game is over and it is only waiting for an admin to approve it, so cancelling it would
     * throw away a played match.
     */
    private static final Set<MatchStatus> RUNNING = EnumSet.of(
            MatchStatus.DRAFT, MatchStatus.TEAMS_DRAWN, MatchStatus.DRAFT_READY,
            MatchStatus.DRAFTING, MatchStatus.DRAFTED, MatchStatus.LOBBY_READY, MatchStatus.LIVE);

    /** The champion draft has actually begun (DRAFT_READY is still waiting for the admin's start). */
    private static final Set<MatchStatus> DRAFT_IN_PROGRESS = EnumSet.of(
            MatchStatus.DRAFTING, MatchStatus.DRAFTED);

    private final MatchRepository matchRepository;
    private final MatchEventRepository eventRepository;
    private final MatchApprovalRepository approvalRepository;
    private final MatchFeedbackRepository feedbackRepository;
    private final MatchDraftRepository draftRepository;
    private final DrawVoteRepository drawVoteRepository;
    private final MatchEventRecorder eventRecorder;

    public MatchMaintenanceService(MatchRepository matchRepository,
                                   MatchEventRepository eventRepository,
                                   MatchApprovalRepository approvalRepository,
                                   MatchFeedbackRepository feedbackRepository,
                                   MatchDraftRepository draftRepository,
                                   DrawVoteRepository drawVoteRepository,
                                   MatchEventRecorder eventRecorder) {
        this.matchRepository = matchRepository;
        this.eventRepository = eventRepository;
        this.approvalRepository = approvalRepository;
        this.feedbackRepository = feedbackRepository;
        this.draftRepository = draftRepository;
        this.drawVoteRepository = drawVoteRepository;
        this.eventRecorder = eventRecorder;
    }

    /** How much each button would affect, so the UI can show numbers and stay disabled at zero. */
    @Transactional(readOnly = true)
    public Summary summary() {
        return new Summary(
                matchRepository.count(),
                matchRepository.countByStatusIn(DRAFT_IN_PROGRESS),
                matchRepository.countByStatusIn(RUNNING),
                matchRepository.countByStatusNot(MatchStatus.APPROVED),
                matchRepository.countByStatus(MatchStatus.APPROVED));
    }

    /** Deletes every match whose champion draft had already started. Irreversible. */
    @Transactional
    public int deleteDraftsInProgress(UUID actor) {
        List<Match> targets = matchRepository.findByStatusIn(DRAFT_IN_PROGRESS);
        targets.forEach(this::purge);
        log.warn("Admin {} deleted {} match(es) with a draft in progress", actor, targets.size());
        return targets.size();
    }

    /**
     * Cancels everything still in flight. Keeps the rows — this is the safe button, for when an
     * evening fell apart and the board has to be cleared without losing the audit trail.
     */
    @Transactional
    public int stopAllRunning(UUID actor) {
        List<Match> targets = matchRepository.findByStatusIn(RUNNING);
        for (Match match : targets) {
            match.transitionTo(MatchStatus.CANCELLED);
            eventRecorder.record(match.getId(), MatchEventType.CANCELLED, actor,
                    java.util.Map.of("reason", "admin-stop-all"));
        }
        log.warn("Admin {} cancelled {} running match(es)", actor, targets.size());
        return targets.size();
    }

    /**
     * Deletes every match that is not APPROVED, leaving only the confirmed record. Irreversible, and
     * it does include RESULTS_SUBMITTED matches that were never approved.
     */
    @Transactional
    public int purgeUnapproved(UUID actor) {
        List<Match> targets = matchRepository.findByStatusNot(MatchStatus.APPROVED);
        targets.forEach(this::purge);
        log.warn("Admin {} purged {} unapproved match(es)", actor, targets.size());
        return targets.size();
    }

    /** Deletes one match outright, whatever its status. Irreversible. */
    @Transactional
    public void delete(UUID id, UUID actor) {
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> pl.romcio.driperska.common.error.ResourceNotFoundException.of("Match", id));
        MatchStatus status = match.getStatus();
        purge(match);
        log.warn("Admin {} deleted match {} (was {})", actor, id, status);
    }

    /** Drops a match and every row that points at it. */
    private void purge(Match match) {
        UUID id = match.getId();
        feedbackRepository.deleteByMatchId(id);
        approvalRepository.deleteByMatchId(id);
        drawVoteRepository.deleteByMatchId(id);
        draftRepository.deleteByMatchId(id);
        eventRepository.deleteByMatchId(id);
        // Participants and the player pool are cascaded from the entity itself.
        matchRepository.delete(match);
    }

    /**
     * @param total          all matches on record
     * @param draftInProgress matches whose champion draft has started (DRAFTING / DRAFTED)
     * @param running        matches still in flight, i.e. what "stop everything" would cancel
     * @param unapproved     matches that are not APPROVED, i.e. what the purge would delete
     * @param approved       matches that would survive a purge
     */
    public record Summary(long total, long draftInProgress, long running,
                          long unapproved, long approved) {}
}
