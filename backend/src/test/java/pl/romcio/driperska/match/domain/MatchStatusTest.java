package pl.romcio.driperska.match.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchStatusTest {

    @Test
    void allowsHappyPath() {
        assertThat(MatchStatus.DRAFT.canTransitionTo(MatchStatus.TEAMS_DRAWN)).isTrue();
        assertThat(MatchStatus.TEAMS_DRAWN.canTransitionTo(MatchStatus.LIVE)).isTrue();
        assertThat(MatchStatus.LIVE.canTransitionTo(MatchStatus.RESULTS_SUBMITTED)).isTrue();
        assertThat(MatchStatus.RESULTS_SUBMITTED.canTransitionTo(MatchStatus.APPROVED)).isTrue();
    }

    @Test
    void allowsReRollAndRejectionLoop() {
        assertThat(MatchStatus.TEAMS_DRAWN.canTransitionTo(MatchStatus.TEAMS_DRAWN)).isTrue();
        assertThat(MatchStatus.RESULTS_SUBMITTED.canTransitionTo(MatchStatus.REJECTED)).isTrue();
        assertThat(MatchStatus.REJECTED.canTransitionTo(MatchStatus.RESULTS_SUBMITTED)).isTrue();
    }

    @Test
    void forbidsIllegalJumps() {
        assertThat(MatchStatus.DRAFT.canTransitionTo(MatchStatus.APPROVED)).isFalse();
        assertThat(MatchStatus.LIVE.canTransitionTo(MatchStatus.APPROVED)).isFalse();
        assertThat(MatchStatus.CANCELLED.canTransitionTo(MatchStatus.DRAFT)).isFalse();
    }

    @Test
    void approvedCanOnlyBeReopened() {
        assertThat(MatchStatus.APPROVED.canTransitionTo(MatchStatus.RESULTS_SUBMITTED)).isTrue();
        assertThat(MatchStatus.APPROVED.canTransitionTo(MatchStatus.CANCELLED)).isFalse();
    }
}
