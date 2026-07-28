package pl.romcio.driperska.match.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.security.AuthenticatedAccount;
import pl.romcio.driperska.match.domain.DrawMode;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchRepository;

/**
 * Guards GET /matches against the regression that made every listing return 500: ordering by
 * {@code startedAt} with {@code NULLS LAST} through a Spring Data derived query blows up with
 * {@code UnsupportedOperationException: Applying Null Precedence using Criteria Queries is not yet
 * supported}. A listing must work for every status filter and must sort started matches above
 * not-yet-started ones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-that-is-at-least-32-bytes-long-ok!!",
        "app.ddragon.sync-on-startup=false"
})
class MatchListingIT {

    @Autowired MockMvc mvc;
    @Autowired MatchRepository matchRepository;

    /** The controller only honours a status filter for a real {@link AuthenticatedAccount} principal. */
    private static Authentication admin() {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccount(UUID.randomUUID(), "admin", "ROLE_ADMIN"), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void listsEveryStatusWithoutFailing() throws Exception {
        for (MatchStatus status : MatchStatus.values()) {
            mvc.perform(get("/api/v1/matches").with(authentication(admin()))
                            .param("status", status.name()).param("size", "50"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/matches").with(authentication(admin())).param("size", "50"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/matches").with(authentication(admin()))
                        .param("status", "RESULTS_SUBMITTED").param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void ordersStartedMatchesBeforeNotYetStartedOnes() throws Exception {
        Match notStarted = persistPending(null);
        Match started = persistPending(Instant.now().plusSeconds(3600));

        mvc.perform(get("/api/v1/matches").with(authentication(admin()))
                        .param("status", "RESULTS_SUBMITTED").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(started.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(notStarted.getId().toString()));
    }

    /** Walks the real lifecycle to RESULTS_SUBMITTED — {@code status} has no setter by design. */
    private Match persistPending(Instant startedAt) {
        Match match = new Match(UUID.randomUUID(), DrawMode.BALANCED, UUID.randomUUID());
        match.setPoolPlayerIds(List.of(UUID.randomUUID()));
        match.replaceParticipants(List.of(new MatchParticipant(UUID.randomUUID(), Side.BLUE, Role.MID)));
        match.transitionTo(MatchStatus.TEAMS_DRAWN);
        match.transitionTo(MatchStatus.LIVE);
        match.transitionTo(MatchStatus.RESULTS_SUBMITTED);
        match.setStartedAt(startedAt);
        return matchRepository.saveAndFlush(match);
    }
}
