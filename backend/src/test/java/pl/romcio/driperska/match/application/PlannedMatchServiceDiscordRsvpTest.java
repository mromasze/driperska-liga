package pl.romcio.driperska.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pl.romcio.driperska.common.config.AppCoreProperties;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.integration.discord.DiscordClient;
import pl.romcio.driperska.match.domain.PlannedMatch;
import pl.romcio.driperska.match.infra.PlannedMatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/** RSVP votes cast from Discord buttons — only linked Discord accounts count. */
class PlannedMatchServiceDiscordRsvpTest {

    private final PlannedMatchRepository planned = mock(PlannedMatchRepository.class);
    private final PlayerRepository players = mock(PlayerRepository.class);
    private final DiscordClient discord = mock(DiscordClient.class);
    private PlannedMatchService service;

    @BeforeEach
    void setUp() {
        AppCoreProperties app = new AppCoreProperties();
        app.setPublicUrl("https://driperska.pl");
        service = new PlannedMatchService(planned, players, discord, app);
    }

    @Test
    void discordVoteOfLinkedPlayerIsCounted() {
        PlannedMatch match = new PlannedMatch(Instant.now().plusSeconds(3600), null, UUID.randomUUID());
        Player player = new Player("Driper", Role.MID, "driper");
        ReflectionTestUtils.setField(player, "id", UUID.randomUUID()); // id is generated on persist
        when(planned.findById(match.getId())).thenReturn(Optional.of(match));
        when(players.findByDiscordUserId("123456789012345678")).thenReturn(Optional.of(player));

        String nickname = service.rsvpByDiscord(match.getId(), "123456789012345678", "YES");

        assertThat(nickname).isEqualTo("Driper");
        assertThat(match.getRsvps()).hasSize(1);
        assertThat(match.getRsvps().getFirst().getPlayerId()).isEqualTo(player.getId());
        assertThat(match.getRsvps().getFirst().getResponse()).isEqualTo("YES");

        // Clicking again changes the vote instead of adding a second one.
        service.rsvpByDiscord(match.getId(), "123456789012345678", "no");
        assertThat(match.getRsvps()).hasSize(1);
        assertThat(match.getRsvps().getFirst().getResponse()).isEqualTo("NO");
    }

    @Test
    void discordVoteOfUnlinkedAccountIsRejected() {
        PlannedMatch match = new PlannedMatch(Instant.now().plusSeconds(3600), null, UUID.randomUUID());
        when(planned.findById(match.getId())).thenReturn(Optional.of(match));
        when(players.findByDiscordUserId(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rsvpByDiscord(match.getId(), "999", "YES"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nie jest połączone");
        assertThat(match.getRsvps()).isEmpty();
    }

    /**
     * The vote message stays in the Discord channel for good, so a click on last month's
     * announcement must not register attendance for a match that is already over.
     */
    @Test
    void discordVoteAfterTheTermHasPassedIsRejected() {
        PlannedMatch match = new PlannedMatch(Instant.now().minusSeconds(60), null, UUID.randomUUID());
        when(planned.findById(match.getId())).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> service.rsvpByDiscord(match.getId(), "123456789012345678", "YES"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Termin tego meczu już minął");
        assertThat(match.getRsvps()).isEmpty();
    }

    @Test
    void webVoteAfterTheTermHasPassedIsRejected() {
        PlannedMatch match = new PlannedMatch(Instant.now().minusSeconds(1), null, UUID.randomUUID());
        when(planned.findById(match.getId())).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> service.rsvp(match.getId(), UUID.randomUUID(), "YES"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Termin tego meczu już minął");
        assertThat(match.getRsvps()).isEmpty();
    }

    /** Players are only offered terms they can still confirm; the admin list keeps the history. */
    @Test
    void theListingForPlayersDropsTermsThatHavePassed() {
        service.listUpcoming(null);
        verify(planned).findByStatusAndScheduledAtGreaterThanEqualOrderByScheduledAtAsc(
                eq(PlannedMatch.PLANNED), any(Instant.class));

        service.listIncludingPast(null);
        verify(planned).findByStatusOrderByScheduledAtAsc(PlannedMatch.PLANNED);
    }

    @Test
    void discordVoteOnCancelledMatchIsRejected() {
        PlannedMatch match = new PlannedMatch(Instant.now().plusSeconds(3600), null, UUID.randomUUID());
        match.cancel();
        when(planned.findById(match.getId())).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> service.rsvpByDiscord(match.getId(), "123456789012345678", "YES"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nie jest już planowany");
        assertThat(match.getRsvps()).isEmpty();
    }
}
