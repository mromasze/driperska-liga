package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import pl.romcio.driperska.account.domain.Account;
import pl.romcio.driperska.account.domain.AccountRole;
import pl.romcio.driperska.account.infra.AccountRepository;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.match.domain.*;
import pl.romcio.driperska.match.infra.*;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/**
 * The bulk admin operations delete rows for good, so what matters here is that they hit exactly the
 * matches they claim to and leave nothing dangling. Only {@code match_draft} has a real
 * {@code ON DELETE CASCADE}; every other satellite table just carries a {@code match_id}, so a
 * regression would show up as orphan rows rather than as an error.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long-ok!!",
        "app.ddragon.sync-on-startup=false",
        "app.draft.poll-ms=3600000"
})
class MatchMaintenanceServiceIT {

    @Autowired MatchMaintenanceService maintenance;
    @Autowired DraftService draftService;
    @Autowired MatchRepository matchRepository;
    @Autowired MatchDraftRepository draftRepository;
    @Autowired MatchEventRepository eventRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired ChampionRepository championRepository;

    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Start from a clean slate: these assertions are about global counts.
        matchRepository.findAll().forEach(m -> maintenance.delete(m.getId(), actor));
        if (championRepository.count() < 40) {
            for (int i = 1; i <= 40; i++) {
                championRepository.save(new Champion(9000 + i, "Champ" + i, "Champ " + i));
            }
        }
    }

    /**
     * The legal route to each status we park a match at — {@link Match#transitionTo} rejects
     * shortcuts, so e.g. APPROVED has to be walked through LIVE and RESULTS_SUBMITTED.
     */
    private static List<MatchStatus> pathTo(MatchStatus target) {
        return switch (target) {
            case DRAFT -> List.of();
            case TEAMS_DRAWN, DRAFTING -> List.of(MatchStatus.TEAMS_DRAWN);
            case LIVE -> List.of(MatchStatus.TEAMS_DRAWN, MatchStatus.LIVE);
            case RESULTS_SUBMITTED -> List.of(
                    MatchStatus.TEAMS_DRAWN, MatchStatus.LIVE, MatchStatus.RESULTS_SUBMITTED);
            case APPROVED -> List.of(MatchStatus.TEAMS_DRAWN, MatchStatus.LIVE,
                    MatchStatus.RESULTS_SUBMITTED, MatchStatus.APPROVED);
            default -> throw new IllegalArgumentException("No fixture path to " + target);
        };
    }

    /** A match with ten real participants, parked in the given status. */
    private Match match(MatchStatus target) {
        List<UUID> playerIds = new ArrayList<>();
        List<MatchParticipant> participants = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String tag = UUID.randomUUID().toString().substring(0, 8);
            Account account = accountRepository.save(
                    new Account("m" + tag, "m" + tag + "@test.local", "{noop}x", AccountRole.PLAYER));
            Player player = new Player("Player-" + tag, Role.MID, "disc-" + tag);
            player.setAccountId(account.getId());
            player = playerRepository.save(player);
            playerIds.add(player.getId());
            participants.add(new MatchParticipant(player.getId(),
                    i < 5 ? Side.BLUE : Side.RED, Role.values()[i % 5]));
        }
        Match created = new Match(UUID.randomUUID(), DrawMode.PURE_RANDOM, actor);
        created.setPoolPlayerIds(playerIds);
        created.replaceParticipants(participants);
        pathTo(target).forEach(created::transitionTo);
        Match saved = matchRepository.saveAndFlush(created);
        // DRAFTING has to come from the service, so the draft row and its event exist to be cleaned up.
        if (target == MatchStatus.DRAFTING) {
            draftService.startDraft(saved.getId(), actor);
        }
        return matchRepository.findById(saved.getId()).orElseThrow();
    }

    @Test
    void summaryCountsWhatEachButtonWouldTouch() {
        match(MatchStatus.DRAFTING);
        match(MatchStatus.LIVE);
        match(MatchStatus.APPROVED);

        var summary = maintenance.summary();

        assertThat(summary.total()).isEqualTo(3);
        assertThat(summary.draftInProgress()).isEqualTo(1);
        assertThat(summary.running()).isEqualTo(2);   // DRAFTING + LIVE
        assertThat(summary.unapproved()).isEqualTo(2);
        assertThat(summary.approved()).isEqualTo(1);
    }

    @Test
    void deletingADraftInProgressTakesItsDraftStateAndEventsWithIt() {
        Match drafting = match(MatchStatus.DRAFTING);
        UUID id = drafting.getId();
        // startDraft persisted a draft row and recorded an event — the things that must not survive.
        assertThat(draftRepository.findById(id)).isPresent();
        assertThat(eventRepository.findByMatchIdOrderByCreatedAtAsc(id)).isNotEmpty();

        assertThat(maintenance.deleteDraftsInProgress(actor)).isEqualTo(1);

        assertThat(matchRepository.findById(id)).isEmpty();
        assertThat(draftRepository.findById(id)).isEmpty();
        assertThat(eventRepository.findByMatchIdOrderByCreatedAtAsc(id)).isEmpty();
    }

    @Test
    void stoppingCancelsRunningMatchesButKeepsThemOnTheList() {
        Match live = match(MatchStatus.LIVE);
        Match approved = match(MatchStatus.APPROVED);

        assertThat(maintenance.stopAllRunning(actor)).isEqualTo(1);

        assertThat(matchRepository.findById(live.getId()))
                .get().extracting(Match::getStatus).isEqualTo(MatchStatus.CANCELLED);
        assertThat(matchRepository.findById(approved.getId()))
                .get().extracting(Match::getStatus).isEqualTo(MatchStatus.APPROVED);
        assertThat(matchRepository.count()).isEqualTo(2);
    }

    @Test
    void stoppingIsIdempotentBecauseCancelledIsNotRunning() {
        match(MatchStatus.LIVE);
        assertThat(maintenance.stopAllRunning(actor)).isEqualTo(1);
        assertThat(maintenance.stopAllRunning(actor)).isZero();
    }

    @Test
    void purgeLeavesOnlyApprovedMatches() {
        match(MatchStatus.DRAFT);
        match(MatchStatus.DRAFTING);
        match(MatchStatus.LIVE);
        match(MatchStatus.RESULTS_SUBMITTED);
        Match approved = match(MatchStatus.APPROVED);

        assertThat(maintenance.purgeUnapproved(actor)).isEqualTo(4);

        assertThat(matchRepository.findAll()).extracting(Match::getId).containsExactly(approved.getId());
    }
}
