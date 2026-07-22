package pl.romcio.driperska.ranking.domain;

import java.util.List;
import java.util.UUID;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;

/** Pure input to the scoring calculators — decoupled from JPA so the domain stays testable. */
public record MatchStatsContext(
        Side winningSide,
        int durationSeconds,
        List<ParticipantInput> participants) {

    public record ParticipantInput(
            UUID participantId,
            UUID playerId,
            Side side,
            Role role,
            int kills,
            int deaths,
            int assists,
            int cs,
            int gold,
            int damageToChampions,
            int visionScore,
            int largestMultiKill) {

        public double kda() {
            return (kills + assists) / (double) Math.max(1, deaths);
        }
    }

    public double minutes() {
        return Math.max(1.0, durationSeconds / 60.0);
    }

    public List<ParticipantInput> team(Side side) {
        return participants.stream().filter(p -> p.side() == side).toList();
    }

    public int teamKills(Side side) {
        return team(side).stream().mapToInt(ParticipantInput::kills).sum();
    }

    public long teamDamage(Side side) {
        return team(side).stream().mapToLong(ParticipantInput::damageToChampions).sum();
    }

    public long teamGold(Side side) {
        return team(side).stream().mapToLong(ParticipantInput::gold).sum();
    }
}
