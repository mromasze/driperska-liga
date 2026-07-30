package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import pl.romcio.driperska.account.domain.Account;
import pl.romcio.driperska.account.domain.AccountRole;
import pl.romcio.driperska.account.infra.AccountRepository;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ForbiddenException;
import pl.romcio.driperska.common.error.InvalidTransitionException;
import pl.romcio.driperska.match.api.MatchDtos.ParticipantResultInput;
import pl.romcio.driperska.match.api.MatchDtos.SubmitResultsRequest;
import pl.romcio.driperska.match.application.MatchSubmissionService.RosterSlot;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;
import pl.romcio.driperska.season.domain.Season;
import pl.romcio.driperska.season.domain.SeasonStatus;
import pl.romcio.driperska.season.infra.SeasonRepository;

/**
 * A moderator submission is the one path into the approval queue that is not driven by an admin, so
 * what matters here is the two rules protecting it: it lands in the queue exactly like an admin-entered
 * result, and nobody can reach a submission that is not theirs (or one an admin already signed off).
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long-ok!!",
        "app.ddragon.sync-on-startup=false",
        "app.draft.poll-ms=3600000"
})
class MatchSubmissionServiceIT {

    @Autowired MatchSubmissionService submissions;
    @Autowired ApprovalService approvalService;
    @Autowired MatchRepository matchRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired ChampionRepository championRepository;
    @Autowired SeasonRepository seasonRepository;

    private UUID moderator;
    private List<UUID> roster;

    @BeforeEach
    void setUp() {
        if (seasonRepository.findFirstByStatus(SeasonStatus.ACTIVE).isEmpty()) {
            Season season = new Season("Test", LocalDate.now().minusMonths(1), null);
            season.setStatus(SeasonStatus.ACTIVE);
            seasonRepository.save(season);
        }
        if (championRepository.count() < 1) {
            championRepository.save(new Champion(8001, "Champ", "Champ"));
        }
        moderator = account(true).getId();
        roster = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            roster.add(player().getId());
        }
    }

    private Account account(boolean isModerator) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Account account = new Account("mod" + tag, "mod" + tag + "@test.local", "{noop}x",
                AccountRole.PLAYER);
        account.setModerator(isModerator);
        return accountRepository.save(account);
    }

    private Player player() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        return playerRepository.save(new Player("P-" + tag, Role.MID, "disc-" + tag));
    }

    private List<RosterSlot> slots() {
        List<RosterSlot> slots = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            slots.add(new RosterSlot(roster.get(i), i < 5 ? Side.BLUE : Side.RED, Role.values()[i % 5]));
        }
        return slots;
    }

    private Match submit(UUID accountId) {
        return submissions.create(accountId, null, Instant.now().minus(2, ChronoUnit.HOURS), slots());
    }

    private SubmitResultsRequest results(Match match) {
        int championId = championRepository.findAll().getFirst().getId();
        List<ParticipantResultInput> participants = match.getParticipants().stream()
                .map(p -> new ParticipantResultInput(p.getPlayerId(), p.getRole(), championId,
                        3, 2, 5, 180, 11000, 15000, 20, 1))
                .toList();
        return new SubmitResultsRequest(Side.BLUE, 1800, "14.13", participants);
    }

    @Test
    void aSubmissionStartsAsAPlayedMatchWaitingForItsStatistics() {
        Match match = submit(moderator);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.LIVE);
        assertThat(match.getCreatedBy()).isEqualTo(moderator);
        assertThat(match.getStartedAt()).isBefore(Instant.now());
        assertThat(match.getParticipants()).hasSize(10);
        // No Riot lobby, no tournament code — a submission never touches the live pipeline.
        assertThat(match.getRiotTournamentCode()).isNull();
    }

    @Test
    void enteringStatisticsPutsTheSubmissionInTheApprovalQueue() {
        Match match = submit(moderator);

        Match queued = submissions.saveResults(moderator, match.getId(), results(match));

        assertThat(queued.getStatus()).isEqualTo(MatchStatus.RESULTS_SUBMITTED);
        assertThat(queued.getWinningSide()).isEqualTo(Side.BLUE);
        assertThat(submissions.listOwn(moderator, PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(s -> assertThat(s.approval()).isNotNull());
    }

    @Test
    void aPendingSubmissionStaysEditable() {
        Match match = submit(moderator);
        submissions.saveResults(moderator, match.getId(), results(match));

        SubmitResultsRequest corrected = new SubmitResultsRequest(Side.RED, 2400, "14.14",
                results(match).participants());
        Match edited = submissions.saveResults(moderator, match.getId(), corrected);

        assertThat(edited.getStatus()).isEqualTo(MatchStatus.RESULTS_SUBMITTED);
        assertThat(edited.getWinningSide()).isEqualTo(Side.RED);
        assertThat(edited.getDurationSeconds()).isEqualTo(2400);
    }

    @Test
    void theRosterIsOnlyEditableBeforeTheResultsAreQueued() {
        Match match = submit(moderator);
        List<RosterSlot> swapped = new ArrayList<>(slots());
        swapped.set(0, new RosterSlot(roster.get(0), Side.RED, Role.TOP));
        swapped.set(5, new RosterSlot(roster.get(5), Side.BLUE, Role.TOP));

        submissions.update(moderator, match.getId(), null, swapped);
        assertThat(matchRepository.findDetailedById(match.getId()).orElseThrow().getParticipants())
                .filteredOn(p -> p.getPlayerId().equals(roster.get(0)))
                .singleElement()
                .satisfies(p -> assertThat(p.getSide()).isEqualTo(Side.RED));

        submissions.saveResults(moderator, match.getId(), results(match));
        assertThatThrownBy(() -> submissions.update(moderator, match.getId(), null, swapped))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void anAccountWithoutThePermissionCannotSubmitAnything() {
        UUID plainPlayer = account(false).getId();

        assertThatThrownBy(() -> submit(plainPlayer)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void oneModeratorCannotTouchAnothersSubmission() {
        Match mine = submit(moderator);
        UUID otherModerator = account(true).getId();

        assertThatThrownBy(() -> submissions.saveResults(otherModerator, mine.getId(), results(mine)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> submissions.cancel(otherModerator, mine.getId()))
                .isInstanceOf(ForbiddenException.class);
        assertThat(submissions.listOwn(otherModerator, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void anApprovedMatchIsFrozenForItsAuthor() {
        Match match = submit(moderator);
        submissions.saveResults(moderator, match.getId(), results(match));
        approvalService.approve(match.getId(),
                new pl.romcio.driperska.match.api.MatchDtos.ApproveRequest(true, "admin"),
                UUID.randomUUID());

        assertThatThrownBy(() -> submissions.saveResults(moderator, match.getId(), results(match)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aMatchThatHasNotHappenedYetIsRefused() {
        assertThatThrownBy(() -> submissions.create(moderator, null,
                Instant.now().plus(2, ChronoUnit.DAYS), slots()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aRosterWithLopsidedTeamsIsRefused() {
        List<RosterSlot> broken = new ArrayList<>(slots());
        broken.set(9, new RosterSlot(roster.get(9), Side.BLUE, Role.TOP));

        assertThatThrownBy(() -> submissions.create(moderator, null,
                Instant.now().minus(1, ChronoUnit.HOURS), broken))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void withdrawingASubmissionTakesItOffTheList() {
        Match match = submit(moderator);

        submissions.cancel(moderator, match.getId());

        assertThat(matchRepository.findById(match.getId()).orElseThrow().getStatus())
                .isEqualTo(MatchStatus.CANCELLED);
        assertThat(submissions.listOwn(moderator, PageRequest.of(0, 10)).getContent()).isEmpty();
    }
}
