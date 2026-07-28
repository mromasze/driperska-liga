package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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
import pl.romcio.driperska.match.api.DrawLobbyDtos.DraftView;
import pl.romcio.driperska.match.application.draft.DraftState;
import pl.romcio.driperska.match.domain.DrawMode;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchDraftRepository;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/**
 * Covers the draft rules that are easy to break and expensive to discover live: whose turn it is, the
 * admin champion override, hover broadcasting, and the guarantee that an expiring timer always leaves
 * the slot filled (never "covered").
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long-ok!!",
        "app.ddragon.sync-on-startup=false",
        // Long enough that the scheduler never interferes; timeout behaviour is driven explicitly.
        "app.draft.step-seconds=3600",
        "app.draft.poll-ms=3600000"
})
class DraftServiceIT {

    @Autowired DraftService draftService;
    @Autowired MatchService matchService;
    @Autowired MatchRepository matchRepository;
    @Autowired MatchDraftRepository draftRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired ChampionRepository championRepository;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private Match match;
    /** playerId → accountId, so a test can act "as" a given player. */
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
                    new Account("p" + tag, "p" + tag + "@test.local", "{noop}x", AccountRole.PLAYER));
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
        match = matchRepository.saveAndFlush(created);

        draftService.startDraft(match.getId(), UUID.randomUUID());
    }

    /**
     * Always reads the match with participants eagerly fetched. {@code matchService.get} hands back a
     * detached entity, and touching its lazy collection from a test method (there is no surrounding
     * transaction here, unlike in the services) would blow up on the proxy rather than test anything.
     */
    private Match detailed() {
        return matchRepository.findDetailedById(match.getId()).orElseThrow();
    }

    private DraftView view() {
        return draftService.view(detailed());
    }

    private UUID accountOf(UUID playerId) {
        return accountIds.get(playerIds.indexOf(playerId));
    }

    /** Drives the canonical sequence, always acting as whoever the server says is on the clock. */
    private void playSteps(int steps) {
        for (int i = 0; i < steps; i++) {
            DraftView v = view();
            UUID actor = accountOf(v.currentPlayerId());
            int champion = firstFreeChampion(v);
            if ("BAN".equals(v.currentType())) draftService.ban(match.getId(), actor, champion);
            else draftService.pick(match.getId(), actor, champion);
        }
    }

    private int firstFreeChampion(DraftView v) {
        List<Integer> taken = new ArrayList<>(v.blueBans());
        taken.addAll(v.redBans());
        detailed().getParticipants().forEach(p -> {
            if (p.getChampionId() != null) taken.add(p.getChampionId());
        });
        return championRepository.findAll().stream()
                .map(Champion::getId)
                .filter(id -> !taken.contains(id))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void startsWithSixBansThenPicksAndNamesTheCaptainForBans() {
        DraftView v = view();
        assertThat(v.status()).isEqualTo("DRAFTING");
        assertThat(v.currentType()).isEqualTo("BAN");
        assertThat(v.currentSide()).isEqualTo(Side.BLUE);
        // Bans are made by the captain, who is the first entry in the side's draft order.
        assertThat(v.currentPlayerId()).isEqualTo(v.blueCaptain());
        assertThat(v.blueOrder()).hasSize(5);
        assertThat(v.stepSeconds()).isEqualTo(3600);

        playSteps(6);
        assertThat(view().currentType()).isEqualTo("PICK");
        assertThat(view().blueBans()).hasSize(3);
        assertThat(view().redBans()).hasSize(3);
    }

    @Test
    void picksFollowTheDraftOrderTopToBottom() {
        playSteps(6); // finish ban phase 1
        DraftView v = view();
        assertThat(v.currentPlayerId()).isEqualTo(v.blueOrder().get(0));

        playSteps(1); // blue's first pick
        v = view();
        assertThat(v.currentSide()).isEqualTo(Side.RED);
        assertThat(v.currentPlayerId()).isEqualTo(v.redOrder().get(0));

        playSteps(2); // red's two picks
        v = view();
        assertThat(v.currentSide()).isEqualTo(Side.BLUE);
        assertThat(v.currentPlayerId()).isEqualTo(v.blueOrder().get(1));
    }

    @Test
    void hoverIsVisibleToEveryoneAndClearsWhenTheStepAdvances() {
        DraftView v = view();
        UUID onClock = v.currentPlayerId();
        int champion = firstFreeChampion(v);

        draftService.hover(match.getId(), accountOf(onClock), champion);
        v = view();
        assertThat(v.hoverChampionId()).isEqualTo(champion);
        assertThat(v.hoverPlayerId()).isEqualTo(onClock);

        draftService.ban(match.getId(), accountOf(onClock), champion);
        assertThat(view().hoverChampionId()).isNull();
    }

    @Test
    void onlyThePlayerOnTheClockMayHover() {
        DraftView v = view();
        UUID other = playerIds.stream().filter(id -> !id.equals(v.currentPlayerId())).findFirst().orElseThrow();
        assertThatThrownBy(() -> draftService.hover(match.getId(), accountOf(other), firstFreeChampion(v)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void adminOverrideChangesAChampionWithoutMovingTheTurn() {
        playSteps(7); // 6 bans + blue's first pick — that player now owns a champion
        DraftView before = view();
        UUID picked = before.blueOrder().get(0);
        MatchParticipant slot = participant(picked);
        assertThat(slot.getChampionId()).isNotNull();

        int replacement = firstFreeChampion(before);
        draftService.adminSetChampion(match.getId(), picked, replacement, UUID.randomUUID());

        assertThat(participant(picked).getChampionId()).isEqualTo(replacement);
        DraftView after = view();
        // The whole point of deriving the picker from the step pointer: correcting a champion must not
        // hand the turn to somebody else.
        assertThat(after.currentIndex()).isEqualTo(before.currentIndex());
        assertThat(after.currentPlayerId()).isEqualTo(before.currentPlayerId());
        assertThat(after.currentSide()).isEqualTo(before.currentSide());
    }

    @Test
    void adminOverrideRejectsAChampionThatIsAlreadyTaken() {
        playSteps(7);
        DraftView v = view();
        UUID first = v.blueOrder().get(0);
        int alreadyBanned = v.blueBans().get(0);
        assertThatThrownBy(() ->
                draftService.adminSetChampion(match.getId(), first, alreadyBanned, UUID.randomUUID()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void timeoutLocksTheHoveredChampionSoTheSlotIsNeverLeftEmpty() {
        playSteps(6); // into the pick phase
        DraftView v = view();
        UUID onClock = v.currentPlayerId();
        int hovered = firstFreeChampion(v);
        draftService.hover(match.getId(), accountOf(onClock), hovered);

        expireCurrentStep();
        draftService.resolveExpired(match.getId());

        assertThat(participant(onClock).getChampionId()).isEqualTo(hovered);
        assertThat(view().currentIndex()).isEqualTo(v.currentIndex() + 1);
        assertThat(view().autoResolvedSteps()).contains(v.currentIndex());
    }

    @Test
    void timeoutWithNoHoverStillAssignsSomething() {
        playSteps(6);
        DraftView v = view();
        UUID onClock = v.currentPlayerId();

        expireCurrentStep();
        draftService.resolveExpired(match.getId());

        assertThat(participant(onClock).getChampionId()).isNotNull();
    }

    @Test
    void everyPlayerEndsWithAChampionAndTheMatchReachesDrafted() {
        playSteps(20); // 10 bans + 10 picks
        assertThat(view().status()).isEqualTo("DONE");
        assertThat(detailed().getStatus()).isEqualTo(MatchStatus.DRAFTED);
        assertThat(detailed().getParticipants())
                .allSatisfy(p -> assertThat(p.getChampionId()).isNotNull());
    }

    private MatchParticipant participant(UUID playerId) {
        return detailed().getParticipants().stream()
                .filter(p -> p.getPlayerId().equals(playerId)).findFirst().orElseThrow();
    }

    /**
     * Backdates the deadline so {@code resolveExpired} treats the step as timed out. The deadline is
     * stored twice — as a column for the scheduler's query and inside the state JSON that
     * {@code resolveExpired} re-checks — so both have to move or the service just returns early.
     */
    private void expireCurrentStep() {
        try {
            var draft = draftRepository.findById(match.getId()).orElseThrow();
            DraftState state = objectMapper.readValue(draft.getState(), DraftState.class);
            state.deadline = Instant.now().minusSeconds(5);
            draft.update(objectMapper.writeValueAsString(state), state.deadline);
            draftRepository.saveAndFlush(draft);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
