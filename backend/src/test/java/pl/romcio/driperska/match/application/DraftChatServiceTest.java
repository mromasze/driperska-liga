package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.match.application.DraftChatService.Scope;
import pl.romcio.driperska.match.domain.DrawMode;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/**
 * Draft chat. The rule that matters is the one the wire has to keep: a team message must never be
 * delivered to an account on the other side. The rest — the cooldown, the length cap, the phase gate —
 * is there so ten people in a lobby cannot accidentally break the stream for each other.
 */
class DraftChatServiceTest {

    private final MatchService matchService = mock(MatchService.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final DrawRealtimeService realtime = mock(DrawRealtimeService.class);
    private DraftChatService chat;

    private final List<Player> squad = new ArrayList<>();
    private Match match;

    @BeforeEach
    void setUp() {
        chat = new DraftChatService(matchService, players, realtime);
        squad.clear();
        List<MatchParticipant> participants = new ArrayList<>();
        List<UUID> playerIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Player player = new Player("P" + i, Role.MID, "disc" + i);
            ReflectionTestUtils.setField(player, "id", UUID.randomUUID());
            player.setAccountId(UUID.randomUUID());
            squad.add(player);
            playerIds.add(player.getId());
            participants.add(new MatchParticipant(player.getId(),
                    i < 5 ? Side.BLUE : Side.RED, Role.values()[i % 5]));
        }
        match = new Match(UUID.randomUUID(), DrawMode.PURE_RANDOM, UUID.randomUUID());
        ReflectionTestUtils.setField(match, "id", UUID.randomUUID());
        match.setPoolPlayerIds(playerIds);
        match.replaceParticipants(participants);
        match.transitionTo(MatchStatus.TEAMS_DRAWN);
        match.transitionTo(MatchStatus.DRAFTING);

        when(matchService.get(match.getId())).thenReturn(match);
        for (Player player : squad) {
            when(players.findByAccountId(player.getAccountId())).thenReturn(Optional.of(player));
        }
        when(players.findByIdIn(anyList())).thenAnswer(invocation -> {
            List<UUID> ids = invocation.getArgument(0);
            return squad.stream().filter(p -> ids.contains(p.getId())).toList();
        });
    }

    private List<UUID> broadcastRecipients() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(realtime).broadcast(captor.capture(), eq(DrawRealtimeService.EVENT_CHAT), any());
        return captor.getValue();
    }

    @Test
    void anAllChatMessageReachesEveryAccountInTheLobby() {
        chat.sendAsPlayer(match.getId(), squad.get(0).getAccountId(), Scope.ALL, "gl hf");

        assertThat(broadcastRecipients())
                .containsExactlyInAnyOrderElementsOf(squad.stream().map(Player::getAccountId).toList());
    }

    @Test
    void aTeamChatMessageNeverLeavesTheTeam() {
        chat.sendAsPlayer(match.getId(), squad.get(0).getAccountId(), Scope.TEAM, "ban Yasuo");

        List<UUID> blueAccounts = squad.subList(0, 5).stream().map(Player::getAccountId).toList();
        List<UUID> redAccounts = squad.subList(5, 10).stream().map(Player::getAccountId).toList();
        assertThat(broadcastRecipients())
                .containsExactlyInAnyOrderElementsOf(blueAccounts)
                .doesNotContainAnyElementsOf(redAccounts);
    }

    @Test
    void historyHidesTheOtherTeamsChannel() {
        chat.sendAsPlayer(match.getId(), squad.get(0).getAccountId(), Scope.TEAM, "blue plan");
        chat.sendAsPlayer(match.getId(), squad.get(5).getAccountId(), Scope.TEAM, "red plan");
        chat.sendAsPlayer(match.getId(), squad.get(1).getAccountId(), Scope.ALL, "gl");

        List<String> blueSees = chat.history(match.getId(), squad.get(0).getAccountId(), false)
                .stream().map(DraftChatService.ChatMessage::text).toList();
        List<String> redSees = chat.history(match.getId(), squad.get(5).getAccountId(), false)
                .stream().map(DraftChatService.ChatMessage::text).toList();

        assertThat(blueSees).containsExactly("blue plan", "gl");
        assertThat(redSees).containsExactly("red plan", "gl");
        // An admin is not in either team, so they see the shared channel only.
        assertThat(chat.history(match.getId(), UUID.randomUUID(), true))
                .extracting(DraftChatService.ChatMessage::text)
                .containsExactly("blue plan", "red plan", "gl");
    }

    @Test
    void anOutsiderCannotWriteIntoTheLobbyChat() {
        UUID strangerAccount = UUID.randomUUID();
        Player stranger = new Player("Nobody", Role.MID, "disc-x");
        ReflectionTestUtils.setField(stranger, "id", UUID.randomUUID());
        when(players.findByAccountId(strangerAccount)).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> chat.sendAsPlayer(match.getId(), strangerAccount, Scope.ALL, "hi"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("uczestnik meczu");
    }

    @Test
    void twoMessagesInARowFromTheSameAccountAreThrottled() {
        chat.sendAsPlayer(match.getId(), squad.get(0).getAccountId(), Scope.ALL, "one");

        assertThatThrownBy(() ->
                chat.sendAsPlayer(match.getId(), squad.get(0).getAccountId(), Scope.ALL, "two"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Zwolnij");
    }

    @Test
    void emptyMessagesAreRefusedAndLongOnesAreCut() {
        assertThatThrownBy(() ->
                chat.sendAsPlayer(match.getId(), squad.get(0).getAccountId(), Scope.ALL, "   "))
                .isInstanceOf(BusinessRuleException.class);

        var sent = chat.sendAsPlayer(match.getId(), squad.get(1).getAccountId(), Scope.ALL,
                "x".repeat(500));
        assertThat(sent.text()).hasSize(300);
    }

    @Test
    void chatIsClosedOutsideTheDraftPhases() {
        match.transitionTo(MatchStatus.DRAFTED);
        match.transitionTo(MatchStatus.LIVE);

        assertThatThrownBy(() ->
                chat.sendAsPlayer(match.getId(), squad.get(0).getAccountId(), Scope.ALL, "hello"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("wokół draftu");
    }

    @Test
    void anAdminMessageGoesToEverybodyAndIsMarkedAsAdmin() {
        var sent = chat.sendAsAdmin(match.getId(), UUID.randomUUID(), "admin", "5 minut przerwy");

        assertThat(sent.admin()).isTrue();
        assertThat(sent.scope()).isEqualTo(Scope.ALL);
        assertThat(broadcastRecipients()).hasSize(10);
    }
}
