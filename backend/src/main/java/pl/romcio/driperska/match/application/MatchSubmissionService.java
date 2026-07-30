package pl.romcio.driperska.match.application;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.account.application.AccountService;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ForbiddenException;
import pl.romcio.driperska.common.error.InvalidTransitionException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.integration.discord.DiscordClient;
import pl.romcio.driperska.match.api.MatchDtos.SubmitResultsRequest;
import pl.romcio.driperska.match.domain.DrawMode;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchApproval;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchApprovalRepository;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;
import pl.romcio.driperska.season.application.SeasonService;

/**
 * Moderator submissions: a past match recorded by hand and sent to the admin approval queue.
 *
 * <p>Deliberately separate from the live pipeline ({@link DrawLobbyService}, the champion draft, Riot
 * lobbies, Discord announcements). A submission is a record of a game that has already been played,
 * so there is nothing to draw, nothing to vote on and nobody to notify — it goes straight to
 * {@link MatchStatus#LIVE} with the roster the moderator typed in, which is the state
 * {@link ResultService} accepts statistics for.
 *
 * <p>Everything here is scoped to the submission's author ({@code match.createdBy}): a moderator can
 * see and edit their own submissions and nothing else, for as long as an admin has not signed the
 * results off. Approval freezes the match — from then on only an admin can reopen it.
 */
@Service
public class MatchSubmissionService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MatchSubmissionService.class);

    /** Ten players, five per side, each role once per side. */
    public static final int REQUIRED_ROSTER_SIZE = 10;

    /**
     * How far into the future a "played at" timestamp may sit. Not zero, because a moderator filling
     * the form right after the game ends can be a minute ahead of the server clock.
     */
    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(10);

    private final MatchRepository matchRepository;
    private final MatchApprovalRepository approvalRepository;
    private final PlayerRepository playerRepository;
    private final SeasonService seasonService;
    private final AccountService accountService;
    private final MatchEventRecorder eventRecorder;
    private final ResultService resultService;
    private final DiscordClient discordClient;

    public MatchSubmissionService(MatchRepository matchRepository,
                                  MatchApprovalRepository approvalRepository,
                                  PlayerRepository playerRepository,
                                  SeasonService seasonService,
                                  AccountService accountService,
                                  MatchEventRecorder eventRecorder,
                                  ResultService resultService,
                                  DiscordClient discordClient) {
        this.matchRepository = matchRepository;
        this.approvalRepository = approvalRepository;
        this.playerRepository = playerRepository;
        this.seasonService = seasonService;
        this.accountService = accountService;
        this.eventRecorder = eventRecorder;
        this.resultService = resultService;
        this.discordClient = discordClient;
    }

    /** One player placed on a side and a role by hand. */
    public record RosterSlot(UUID playerId, Side side, Role role) {}

    /** A submission plus the approval state the moderator needs to see (reason for a send-back). */
    public record Submission(Match match, MatchApproval approval) {}

    // ---- reads -------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Submission> listOwn(UUID accountId, Pageable pageable) {
        requireModerator(accountId);
        Page<Match> matches = matchRepository.findSubmissionsByCreator(
                accountId, MatchStatus.CANCELLED, pageable);
        Map<UUID, MatchApproval> approvals = approvalRepository
                .findByMatchIdIn(matches.getContent().stream().map(Match::getId).toList())
                .stream().collect(Collectors.toMap(MatchApproval::getMatchId, a -> a));
        return matches.map(match -> new Submission(match, approvals.get(match.getId())));
    }

    // ---- writes ------------------------------------------------------------------------------

    /**
     * Records the roster of a played match. The result is a match in {@code LIVE} with no statistics
     * yet — the moderator fills those in next (by hand or from screenshots) and submits them.
     */
    @Transactional
    public Match create(UUID accountId, UUID seasonId, Instant playedAt, List<RosterSlot> roster) {
        requireModerator(accountId);
        UUID season = seasonId != null ? seasonService.get(seasonId).getId() : seasonService.current().getId();
        Instant startedAt = requirePastOrNow(playedAt);
        validateRoster(roster);

        List<UUID> pool = roster.stream().map(RosterSlot::playerId).toList();
        Match match = new Match(season, DrawMode.MANUAL, accountId);
        match.setPoolPlayerIds(pool);
        Match saved = matchRepository.save(match);
        saved.replaceParticipants(toParticipants(roster));

        // DRAFT → TEAMS_DRAWN → LIVE in one go: the teams are a given (they are history), and LIVE is
        // the state results can be entered for.
        saved.transitionTo(MatchStatus.TEAMS_DRAWN);
        saved.setTeamsDrawnAt(Instant.now());
        saved.transitionTo(MatchStatus.LIVE);
        saved.setStartedAt(startedAt);

        eventRecorder.record(saved.getId(), MatchEventType.CREATED, accountId,
                Map.of("pool", pool, "source", "MODERATOR_SUBMISSION"));
        eventRecorder.record(saved.getId(), MatchEventType.MATCH_STARTED, accountId,
                Map.of("manual", true, "playedAt", startedAt.toString()));
        return saved;
    }

    /**
     * Corrects the date and — while no results have been submitted yet — the roster of a pending
     * submission. Replacing the roster drops every statistic entered so far, because they belong to
     * the participants being removed; that is why it is refused once the results are in the queue.
     */
    @Transactional
    public Match update(UUID accountId, UUID matchId, Instant playedAt, List<RosterSlot> roster) {
        Match match = ownedAndEditable(accountId, matchId);
        if (playedAt != null) {
            match.setStartedAt(requirePastOrNow(playedAt));
        }
        if (roster != null) {
            if (match.getStatus() != MatchStatus.LIVE && match.getStatus() != MatchStatus.REJECTED) {
                throw new InvalidTransitionException(
                        "Skład można zmienić tylko przed wysłaniem wyników albo po odesłaniu wniosku do poprawy");
            }
            validateRoster(roster);
            match.setPoolPlayerIds(roster.stream().map(RosterSlot::playerId).toList());
            match.replaceParticipants(toParticipants(roster));
            eventRecorder.record(matchId, MatchEventType.TEAMS_DRAWN, accountId,
                    Map.of("rosterEdited", true));
        }
        return match;
    }

    /**
     * Saves statistics and (re)sends the submission to the approval queue. Editing an already-pending
     * submission is allowed as often as the moderator likes; the admin is pinged on Discord only when
     * the match actually enters the queue, so corrections don't spam the channel.
     */
    @Transactional
    public Match saveResults(UUID accountId, UUID matchId, SubmitResultsRequest req) {
        Match match = ownedAndEditable(accountId, matchId);
        boolean wasQueued = match.getStatus() == MatchStatus.RESULTS_SUBMITTED;
        Match saved = resultService.saveResults(matchId, req, accountId);
        if (!wasQueued && saved.getStatus() == MatchStatus.RESULTS_SUBMITTED) {
            notifyAdmins(accountId, saved);
        }
        return saved;
    }

    /** Withdraws a submission that has not been approved. */
    @Transactional
    public Match cancel(UUID accountId, UUID matchId) {
        Match match = ownedAndEditable(accountId, matchId);
        match.transitionTo(MatchStatus.CANCELLED);
        eventRecorder.record(matchId, MatchEventType.CANCELLED, accountId,
                Map.of("withdrawnByModerator", true));
        return match;
    }

    /** The match behind a submission, for the OCR pass — same ownership rules as an edit. */
    @Transactional(readOnly = true)
    public UUID requireOwnEditable(UUID accountId, UUID matchId) {
        return ownedAndEditable(accountId, matchId).getId();
    }

    // ---- guards ------------------------------------------------------------------------------

    public void requireModerator(UUID accountId) {
        if (!accountService.isModerator(accountId)) {
            throw new ForbiddenException("To konto nie ma uprawnień moderatora");
        }
    }

    private Match ownedAndEditable(UUID accountId, UUID matchId) {
        requireModerator(accountId);
        Match match = matchRepository.findDetailedById(matchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", matchId));
        if (!accountId.equals(match.getCreatedBy())) {
            throw new ForbiddenException("Ten wniosek został wprowadzony przez kogoś innego");
        }
        if (match.getStatus() == MatchStatus.APPROVED) {
            throw new ForbiddenException(
                    "Mecz jest już zatwierdzony — zmiany może wprowadzić tylko administrator");
        }
        if (match.getStatus() == MatchStatus.CANCELLED) {
            throw new InvalidTransitionException("Ten wniosek został wycofany");
        }
        return match;
    }

    private Instant requirePastOrNow(Instant playedAt) {
        if (playedAt == null) {
            throw new BusinessRuleException("Podaj datę i godzinę rozegrania meczu");
        }
        if (playedAt.isAfter(Instant.now().plus(MAX_CLOCK_SKEW))) {
            throw new BusinessRuleException("Data rozegrania nie może być w przyszłości");
        }
        return playedAt;
    }

    private void validateRoster(List<RosterSlot> roster) {
        if (roster == null || roster.size() != REQUIRED_ROSTER_SIZE) {
            throw new BusinessRuleException(
                    "Skład musi obejmować dokładnie %d graczy".formatted(REQUIRED_ROSTER_SIZE));
        }
        Set<UUID> ids = roster.stream().map(RosterSlot::playerId).collect(Collectors.toSet());
        if (ids.size() != roster.size()) {
            throw new BusinessRuleException("Gracz nie może wystąpić w meczu dwukrotnie");
        }
        for (Side side : Side.values()) {
            List<RosterSlot> onSide = roster.stream().filter(s -> s.side() == side).toList();
            if (onSide.size() != REQUIRED_ROSTER_SIZE / 2) {
                throw new BusinessRuleException("Każda drużyna musi mieć dokładnie %d graczy"
                        .formatted(REQUIRED_ROSTER_SIZE / 2));
            }
            if (new HashSet<>(onSide.stream().map(RosterSlot::role).toList()).size() != onSide.size()) {
                throw new BusinessRuleException("Każda rola w drużynie może wystąpić tylko raz");
            }
        }
        Map<UUID, Player> players = new HashMap<>();
        playerRepository.findByIdIn(List.copyOf(ids)).forEach(p -> players.put(p.getId(), p));
        List<String> missing = ids.stream().filter(id -> !players.containsKey(id)).map(UUID::toString).toList();
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("Niektórzy gracze ze składu nie istnieją: "
                    + String.join(", ", missing));
        }
    }

    private static List<MatchParticipant> toParticipants(List<RosterSlot> roster) {
        List<MatchParticipant> participants = new ArrayList<>(roster.size());
        for (RosterSlot slot : roster) {
            participants.add(new MatchParticipant(slot.playerId(), slot.side(), slot.role()));
        }
        return participants;
    }

    /**
     * Tells the admins a submission is waiting. Best-effort on purpose: a missing bot token or a
     * Discord outage must not fail the submission the moderator just spent ten minutes typing in.
     */
    private void notifyAdmins(UUID accountId, Match match) {
        String author = accountService.find(accountId).map(a -> a.getUsername()).orElse("moderator");
        String nicknames = playerRepository.findByIdIn(match.getPoolPlayerIds()).stream()
                .map(Player::getNickname).sorted().collect(Collectors.joining(", "));
        String winner = match.getWinningSide() == Side.BLUE ? "Niebiescy" : "Czerwoni";
        String content = """
                🗒️ **Nowy mecz do akceptacji**
                Wprowadził: **%s**
                Wynik: **%s**
                Skład: %s
                Kolejka akceptacji jest w panelu administratora."""
                .formatted(author, winner, nicknames);
        DiscordClient.Delivery delivery = discordClient.sendModerationNotice(content);
        if (!delivery.sent()) {
            // The approval queue in the admin panel is the source of truth; the ping is a courtesy.
            log.warn("Moderator submission {} was queued but Discord was not notified: {}",
                    match.getId(), delivery.message());
        }
    }
}
