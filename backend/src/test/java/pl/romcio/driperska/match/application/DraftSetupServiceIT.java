package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.InvalidTransitionException;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DraftSetupView;
import pl.romcio.driperska.match.domain.DrawMode;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/**
 * The phase between "squad confirmed" and the first ban: each team votes a captain in, that captain
 * arranges the pick order, both declare themselves ready and the draft starts on its own. What is
 * worth testing here is that a team cannot be captained by an outsider, that the vote resolves
 * decisively, that the captain's order is what the draft actually uses, and that nothing in this phase
 * is mandatory — a silent team still gets a draft.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long-ok!!",
        "app.ddragon.sync-on-startup=false",
        "app.draft.step-seconds=3600",
        "app.draft.poll-ms=3600000"
})
class DraftSetupServiceIT {

    @Autowired DraftSetupService setupService;
    @Autowired DraftService draftService;
    @Autowired MatchRepository matchRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired ChampionRepository championRepository;

    private Match match;
    private final List<UUID> playerIds = new ArrayList<>();
    private final List<UUID> accountIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        if (championRepository.count() < 40) {
            for (int i = 1; i <= 40; i++) {
                championRepository.save(new Champion(9000 + i, "Champ" + i, "Champ " + i));
            }
        }
        playerIds.clear();
        accountIds.clear();
        List<MatchParticipant> participants = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String tag = UUID.randomUUID().toString().substring(0, 8);
            Account account = accountRepository.save(
                    new Account("c" + tag, "c" + tag + "@test.local", "{noop}x", AccountRole.PLAYER));
            Player player = new Player("Player-" + tag, Role.MID, "disc-" + tag);
            player.setAccountId(account.getId());
            player = playerRepository.save(player);
            playerIds.add(player.getId());
            accountIds.add(account.getId());
            participants.add(new MatchParticipant(player.getId(),
                    i < 5 ? Side.BLUE : Side.RED, Role.values()[i % 5]));
        }
        Match created = new Match(UUID.randomUUID(), DrawMode.PURE_RANDOM, UUID.randomUUID());
        created.setPoolPlayerIds(playerIds);
        created.replaceParticipants(participants);
        created.transitionTo(MatchStatus.TEAMS_DRAWN);
        created.transitionTo(MatchStatus.DRAFT_READY);
        match = matchRepository.saveAndFlush(created);
    }

    /** Blue is players 0–4, red is 5–9. */
    private UUID account(int index) {
        return accountIds.get(index);
    }

    private Match detailed() {
        return matchRepository.findDetailedById(match.getId()).orElseThrow();
    }

    private DraftSetupView setup() {
        return setupService.view(detailed());
    }

    /** Three of blue vote for player 2. */
    private void electBlueCaptain(int candidate) {
        setupService.voteCaptain(match.getId(), account(0), playerIds.get(candidate));
        setupService.voteCaptain(match.getId(), account(1), playerIds.get(candidate));
        setupService.voteCaptain(match.getId(), account(2), playerIds.get(candidate));
    }

    private void electRedCaptain(int candidate) {
        setupService.voteCaptain(match.getId(), account(5), playerIds.get(candidate));
        setupService.voteCaptain(match.getId(), account(6), playerIds.get(candidate));
        setupService.voteCaptain(match.getId(), account(7), playerIds.get(candidate));
    }

    @Test
    void aFreshSetupHasNoCaptainsAndNobodyReady() {
        DraftSetupView view = setup();

        assertThat(view).isNotNull();
        assertThat(view.votesToDecide()).isEqualTo(3);
        assertThat(view.blue().captain()).isNull();
        assertThat(view.blue().squadSize()).isEqualTo(5);
        assertThat(view.blue().votes()).hasSize(5);
        assertThat(view.blue().ready()).isFalse();
        assertThat(view.red().captain()).isNull();
    }

    @Test
    void threeOfFiveVotesSettleTheCaptain() {
        setupService.voteCaptain(match.getId(), account(0), playerIds.get(2));
        setupService.voteCaptain(match.getId(), account(1), playerIds.get(2));
        assertThat(setup().blue().captain()).as("two votes is not a majority").isNull();

        setupService.voteCaptain(match.getId(), account(2), playerIds.get(2));

        assertThat(setup().blue().captain()).isEqualTo(playerIds.get(2));
        assertThat(setup().blue().votesCast()).isEqualTo(3);
        // The other team is untouched by blue's vote.
        assertThat(setup().red().captain()).isNull();
    }

    @Test
    void aSplitVoteStillProducesACaptainOnceEveryoneHasVoted() {
        setupService.voteCaptain(match.getId(), account(0), playerIds.get(0));
        setupService.voteCaptain(match.getId(), account(1), playerIds.get(0));
        setupService.voteCaptain(match.getId(), account(2), playerIds.get(1));
        setupService.voteCaptain(match.getId(), account(3), playerIds.get(1));
        assertThat(setup().blue().captain()).isNull();

        setupService.voteCaptain(match.getId(), account(4), playerIds.get(4));

        // 2-2-1 has no majority, but stalling the draft over it would be worse than a coin toss.
        assertThat(setup().blue().captain()).isIn(playerIds.get(0), playerIds.get(1));
    }

    @Test
    void youCannotVoteForSomebodyFromTheOtherTeam() {
        assertThatThrownBy(() -> setupService.voteCaptain(match.getId(), account(0), playerIds.get(7)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("we własnej drużynie");
    }

    @Test
    void theCaptainsOrderIsTheOrderTheDraftPicksIn() {
        electBlueCaptain(2);
        List<UUID> chosen = List.of(playerIds.get(4), playerIds.get(2), playerIds.get(0),
                playerIds.get(3), playerIds.get(1));

        setupService.setOrder(match.getId(), account(2), chosen);

        assertThat(setup().blue().order()).containsExactlyElementsOf(chosen);

        draftService.startDraft(match.getId(), UUID.randomUUID());
        var draft = draftService.view(detailed());
        assertThat(draft.blueOrder()).containsExactlyElementsOf(chosen);
        // The captain bans for the team even though they pick second here.
        assertThat(draft.blueCaptain()).isEqualTo(playerIds.get(2));
    }

    @Test
    void onlyTheCaptainMaySetTheOrderOrDeclareReady() {
        electBlueCaptain(2);
        List<UUID> order = List.of(playerIds.get(0), playerIds.get(1), playerIds.get(2),
                playerIds.get(3), playerIds.get(4));

        assertThatThrownBy(() -> setupService.setOrder(match.getId(), account(1), order))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("kapitan");
        assertThatThrownBy(() -> setupService.setReady(match.getId(), account(1), true))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("kapitan");
    }

    @Test
    void anOrderThatIsNotExactlyTheFiveOfTheTeamIsRefused() {
        electBlueCaptain(2);

        assertThatThrownBy(() -> setupService.setOrder(match.getId(), account(2),
                List.of(playerIds.get(0), playerIds.get(7))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dokładnie tych pięciu");
    }

    @Test
    void aTeamCannotBeReadyBeforeItHasACaptain() {
        assertThatThrownBy(() -> setupService.adminSetReady(match.getId(), Side.BLUE, true, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("kapitana");
    }

    @Test
    void bothTeamsReadyStartsTheDraftByItself() {
        electBlueCaptain(2);
        electRedCaptain(6);

        setupService.setReady(match.getId(), account(2), true);
        assertThat(matchRepository.findById(match.getId()).orElseThrow().getStatus())
                .as("one team ready is not enough")
                .isEqualTo(MatchStatus.DRAFT_READY);

        setupService.setReady(match.getId(), account(6), true);

        assertThat(matchRepository.findById(match.getId()).orElseThrow().getStatus())
                .isEqualTo(MatchStatus.DRAFTING);
        var draft = draftService.view(detailed());
        assertThat(draft).isNotNull();
        assertThat(draft.blueCaptain()).isEqualTo(playerIds.get(2));
        assertThat(draft.redCaptain()).isEqualTo(playerIds.get(6));
        // The setup view is gone once the draft is running — the board takes over the screen.
        assertThat(setup()).isNull();
    }

    @Test
    void aSilentTeamStillGetsACaptainAndAnOrderAtStart() {
        draftService.startDraft(match.getId(), UUID.randomUUID());

        var draft = draftService.view(detailed());
        assertThat(draft.blueOrder()).hasSize(5).doesNotContainNull();
        assertThat(draft.redOrder()).hasSize(5);
        assertThat(draft.blueCaptain()).isIn(playerIds.subList(0, 5).toArray());
        assertThat(draft.redCaptain()).isIn(playerIds.subList(5, 10).toArray());
    }

    @Test
    void theVoteAndOrderSurviveAnAdminRerollOfTheDraft() {
        electBlueCaptain(2);
        electRedCaptain(6);
        List<UUID> chosen = List.of(playerIds.get(4), playerIds.get(3), playerIds.get(2),
                playerIds.get(1), playerIds.get(0));
        setupService.setOrder(match.getId(), account(2), chosen);
        draftService.startDraft(match.getId(), UUID.randomUUID());

        draftService.reset(match.getId(), UUID.randomUUID());

        var draft = draftService.view(detailed());
        assertThat(draft.blueCaptain()).isEqualTo(playerIds.get(2));
        assertThat(draft.blueOrder()).containsExactlyElementsOf(chosen);
    }

    @Test
    void anAdminCanAppointACaptainAndResetTheWholeVote() {
        setupService.adminSetCaptain(match.getId(), Side.RED, playerIds.get(9), UUID.randomUUID());
        assertThat(setup().red().captain()).isEqualTo(playerIds.get(9));

        setupService.adminReset(match.getId());

        assertThat(setup().red().captain()).isNull();
        assertThat(setup().red().votesCast()).isZero();
    }

    @Test
    void anAdminCannotAppointSomebodyFromTheOtherTeam() {
        assertThatThrownBy(() -> setupService.adminSetCaptain(
                match.getId(), Side.RED, playerIds.get(0), UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nie jest w tej drużynie");
    }

    @Test
    void theSetupPhaseIsClosedOnceTheDraftIsRunning() {
        draftService.startDraft(match.getId(), UUID.randomUUID());

        assertThatThrownBy(() -> setupService.voteCaptain(match.getId(), account(0), playerIds.get(1)))
                .isInstanceOf(InvalidTransitionException.class);
    }
}
