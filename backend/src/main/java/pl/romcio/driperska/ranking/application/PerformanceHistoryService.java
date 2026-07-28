package pl.romcio.driperska.ranking.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.ranking.domain.MatchStatsContext;
import pl.romcio.driperska.ranking.domain.MatchStatsContext.ParticipantInput;
import pl.romcio.driperska.ranking.domain.PerformanceRatingV2Calculator.PerformanceHistory;

/** Builds the chronological, role-specific PR v2 reference without future-match leakage. */
@Service
public class PerformanceHistoryService {

    private final MatchRepository matchRepository;

    public PerformanceHistoryService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    @Transactional(readOnly = true)
    public PerformanceHistory before(Match target) {
        PerformanceHistory history = new PerformanceHistory();
        Instant targetCompletedAt = target.getCompletedAt();
        approvedInSeason(target).stream()
                .filter(match -> !match.getId().equals(target.getId()))
                .filter(match -> targetCompletedAt == null
                        || (match.getCompletedAt() != null
                        && match.getCompletedAt().isBefore(targetCompletedAt)))
                .forEach(match -> history.add(toContext(match)));
        return history;
    }

    @Transactional(readOnly = true)
    public List<Match> approvedInSeason(Match target) {
        return matchRepository.findByStatusOrderByCompletedAtDesc(MatchStatus.APPROVED).stream()
                .filter(match -> match.getSeasonId().equals(target.getSeasonId()))
                .sorted(Comparator.comparing(Match::getCompletedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public static MatchStatsContext toContext(Match match) {
        List<ParticipantInput> inputs = new ArrayList<>();
        for (MatchParticipant participant : match.getParticipants()) {
            inputs.add(new ParticipantInput(
                    participant.getId(), participant.getPlayerId(), participant.getSide(),
                    participant.getRole(), participant.getKills(), participant.getDeaths(),
                    participant.getAssists(), participant.getCs(), participant.getGold(),
                    participant.getDamageToChampions(), participant.getVisionScore(),
                    participant.getLargestMultiKill()));
        }
        int duration = match.getDurationSeconds() != null ? match.getDurationSeconds() : 1800;
        return new MatchStatsContext(match.getWinningSide(), duration, inputs);
    }
}
