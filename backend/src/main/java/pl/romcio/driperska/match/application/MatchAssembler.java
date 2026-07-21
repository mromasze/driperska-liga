package pl.romcio.driperska.match.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.match.api.MatchDtos.ApprovalResponse;
import pl.romcio.driperska.match.api.MatchDtos.MatchResponse;
import pl.romcio.driperska.match.api.MatchDtos.MatchSummaryResponse;
import pl.romcio.driperska.match.api.MatchDtos.ParticipantResponse;
import pl.romcio.driperska.match.api.MatchDtos.RiotInfoResponse;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchApproval;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.infra.MatchApprovalRepository;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/** Builds enriched match response DTOs (player nicknames, champion art, approval state). */
@Component
public class MatchAssembler {

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final ChampionRepository championRepository;
    private final MatchApprovalRepository approvalRepository;

    public MatchAssembler(MatchRepository matchRepository,
                          PlayerRepository playerRepository,
                          ChampionRepository championRepository,
                          MatchApprovalRepository approvalRepository) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.championRepository = championRepository;
        this.approvalRepository = approvalRepository;
    }

    /** Reloads the match inside this read-only transaction so lazy collections resolve safely. */
    @Transactional(readOnly = true)
    public MatchResponse toResponse(Match detached) {
        Match match = matchRepository.findById(detached.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Match", detached.getId()));
        Map<UUID, Player> players = playersFor(match);
        Map<Integer, Champion> champions = championsFor(match);
        List<ParticipantResponse> participants = match.getParticipants().stream()
                .map(p -> toParticipant(p, players, champions))
                .toList();
        ApprovalResponse approval = approvalRepository.findByMatchId(match.getId())
                .map(MatchAssembler::toApproval)
                .orElse(null);
        return new MatchResponse(
                match.getId(), match.getSeasonId(), match.getStatus(), match.getDrawMode(),
                match.getWinningSide(), match.getDurationSeconds(), match.getPatch(),
                match.getCreatedAt(), match.getStartedAt(), match.getCompletedAt(),
                participants, approval,
                new RiotInfoResponse(match.getRiotTournamentCode(), match.getRiotGameId(),
                        match.getRiotMatchId(), match.getRiotLobbyCreatedAt(),
                        match.getRiotCallbackReceivedAt(), match.getRiotResultsImportedAt(),
                        match.getRiotImportError()));
    }

    public MatchSummaryResponse toSummary(Match match) {
        return new MatchSummaryResponse(
                match.getId(), match.getSeasonId(), match.getStatus(), match.getWinningSide(),
                match.getDurationSeconds(), match.getCreatedAt(), match.getCompletedAt(),
                match.getParticipants().size());
    }

    private ParticipantResponse toParticipant(MatchParticipant p, Map<UUID, Player> players,
                                              Map<Integer, Champion> champions) {
        Player player = players.get(p.getPlayerId());
        Champion champion = p.getChampionId() == null ? null : champions.get(p.getChampionId());
        return new ParticipantResponse(
                p.getPlayerId(),
                player != null ? player.getNickname() : "?",
                player != null ? player.getAvatarUrl() : null,
                p.getSide(), p.getRole(), p.getChampionId(),
                champion != null ? champion.getName() : null,
                champion != null ? champion.getIconUrl() : null,
                p.getKills(), p.getDeaths(), p.getAssists(), round2(p.kda()),
                p.getCs(), p.getGold(), p.getDamageToChampions(), p.getVisionScore(),
                p.getLargestMultiKill(), p.getPerformanceRating(), p.getLpAwarded(), p.isMvp());
    }

    private Map<UUID, Player> playersFor(Match match) {
        List<UUID> ids = match.getParticipants().stream().map(MatchParticipant::getPlayerId).toList();
        Map<UUID, Player> map = new HashMap<>();
        playerRepository.findByIdIn(ids).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    private Map<Integer, Champion> championsFor(Match match) {
        Map<Integer, Champion> map = new HashMap<>();
        match.getParticipants().stream()
                .map(MatchParticipant::getChampionId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .forEach(id -> championRepository.findById(id).ifPresent(c -> map.put(id, c)));
        return map;
    }

    private static ApprovalResponse toApproval(MatchApproval a) {
        return new ApprovalResponse(a.getDecision(), a.getSubmittedBy(), a.getSubmittedAt(),
                a.getReviewedBy(), a.getReviewedAt(), a.isSignatureConfirmed(),
                a.getSignatureName(), a.getRejectionReason());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
