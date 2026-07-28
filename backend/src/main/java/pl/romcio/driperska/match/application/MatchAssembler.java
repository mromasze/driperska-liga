package pl.romcio.driperska.match.application;

import static pl.romcio.driperska.ranking.application.PerformanceHistoryService.toContext;

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
import pl.romcio.driperska.match.api.MatchDtos.LpBreakdown;
import pl.romcio.driperska.match.api.MatchDtos.LpComponent;
import pl.romcio.driperska.match.api.MatchDtos.ParticipantResponse;
import pl.romcio.driperska.match.api.MatchDtos.PrMetric;
import pl.romcio.driperska.match.api.MatchDtos.RiotInfoResponse;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.ranking.application.PerformanceHistoryService;
import pl.romcio.driperska.ranking.application.ScoringConfigProvider;
import pl.romcio.driperska.ranking.domain.PerformanceRatingV2Calculator;
import pl.romcio.driperska.ranking.domain.PointsEngine;
import pl.romcio.driperska.ranking.domain.PointsEngine.PointsBreakdown;
import pl.romcio.driperska.ranking.domain.RatingCalculator;
import pl.romcio.driperska.ranking.domain.ScoringConfig;
import java.util.ArrayList;
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
    private final ScoringConfigProvider scoringConfigProvider;
    private final PerformanceRatingV2Calculator ratingCalculator;
    private final PerformanceHistoryService historyService;
    private final PointsEngine pointsEngine;

    public MatchAssembler(MatchRepository matchRepository,
                          PlayerRepository playerRepository,
                          ChampionRepository championRepository,
                          MatchApprovalRepository approvalRepository,
                          ScoringConfigProvider scoringConfigProvider,
                          PerformanceRatingV2Calculator ratingCalculator,
                          PerformanceHistoryService historyService,
                          PointsEngine pointsEngine) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.championRepository = championRepository;
        this.approvalRepository = approvalRepository;
        this.scoringConfigProvider = scoringConfigProvider;
        this.ratingCalculator = ratingCalculator;
        this.historyService = historyService;
        this.pointsEngine = pointsEngine;
    }

    /** Reloads the match inside this read-only transaction so lazy collections resolve safely. */
    @Transactional(readOnly = true)
    public MatchResponse toResponse(Match detached) {
        Match match = matchRepository.findById(detached.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Match", detached.getId()));
        Map<UUID, Player> players = playersFor(match);
        Map<Integer, Champion> champions = championsFor(match);
        Map<UUID, ParticipantScoring> breakdowns = participantScoring(match);
        List<ParticipantResponse> participants = match.getParticipants().stream()
                .map(p -> toParticipant(p, players, champions, breakdowns.get(p.getPlayerId())))
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
                        match.getRiotImportError()),
                match.getReplayUrl());
    }

    public MatchSummaryResponse toSummary(Match match) {
        return new MatchSummaryResponse(
                match.getId(), match.getSeasonId(), match.getStatus(), match.getWinningSide(),
                match.getDurationSeconds(), match.getCreatedAt(), match.getStartedAt(),
                match.getCompletedAt(), match.getParticipants().size());
    }

    private ParticipantResponse toParticipant(MatchParticipant p, Map<UUID, Player> players,
                                              Map<Integer, Champion> champions, ParticipantScoring scoring) {
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
                p.getLargestMultiKill(), p.getPerformanceRating(), p.getLpAwarded(), p.isMvp(), p.isAce(),
                scoring != null && scoring.bestKda(), scoring != null && scoring.perfectKda(),
                scoring != null ? scoring.lpBreakdown() : null);
    }

    /**
     * Explains each player's LP for a scored match — mirrors {@link pl.romcio.driperska.ranking.domain.PointsEngine}
     * so the total matches the awarded LP. Empty until the match has a winning side + ratings.
     */
    private Map<UUID, ParticipantScoring> participantScoring(Match match) {
        Side winning = match.getWinningSide();
        if (winning == null) return Map.of();
        ScoringConfig cfg = scoringConfigProvider.forSeason(match.getSeasonId());
        var context = toContext(match);
        Map<UUID, RatingCalculator.PrDetail> prDetails =
                ratingCalculator.computeDetailed(context, cfg, historyService.before(match));
        Map<UUID, Double> ratings = new HashMap<>();
        for (MatchParticipant participant : match.getParticipants()) {
            ratings.put(participant.getId(), participant.getPerformanceRating() == null
                    ? 0.0 : participant.getPerformanceRating());
        }
        Map<UUID, PointsBreakdown> points = pointsEngine.computeLeaguePoints(context, ratings, cfg);
        Map<UUID, ParticipantScoring> out = new HashMap<>();
        for (MatchParticipant p : match.getParticipants()) {
            if (p.getPerformanceRating() == null && p.getLpAwarded() == null) continue;
            PointsBreakdown breakdown = points.getOrDefault(
                    p.getId(), new PointsBreakdown(0, false, false, false, false));
            out.put(p.getPlayerId(), new ParticipantScoring(
                    lpBreakdownFor(p, winning, cfg, prDetails.get(p.getId()), breakdown),
                    breakdown.bestKda(), breakdown.perfectKda()));
        }
        return out;
    }

    private static LpBreakdown lpBreakdownFor(MatchParticipant p, Side winning, ScoringConfig cfg,
                                              RatingCalculator.PrDetail prDetail,
                                              PointsBreakdown breakdown) {
        boolean won = p.getSide() == winning;
        double pr = p.getPerformanceRating() == null ? 0.0 : p.getPerformanceRating();
        List<LpComponent> c = new ArrayList<>();
        c.add(new LpComponent(won ? "Zwycięstwo" : "Porażka", won ? cfg.lpWin() : cfg.lpLoss()));
        c.add(new LpComponent("Próg występu (PR " + Math.round(pr) + ")",
                PointsEngine.performancePoints(pr)));
        if (p.isMvp()) c.add(new LpComponent("MVP meczu", cfg.lpMvpBonus()));
        if (p.isAce()) {
            c.add(new LpComponent(p.isMvp()
                    ? "ACE (tytuł — bonus zawarty w MVP)"
                    : "ACE przegranej drużyny", p.isMvp() ? 0 : cfg.lpAceBonus()));
        }
        if (breakdown.bestKda()) {
            c.add(new LpComponent("Najlepsze KDA w meczu", cfg.lpBestKdaBonus()));
        }
        if (breakdown.perfectKda()) {
            c.add(new LpComponent("Perfect KDA (0 śmierci)", cfg.lpPerfectKdaBonus()));
        }
        int total = Math.max(0, c.stream().mapToInt(LpComponent::points).sum());
        String formula = "Baza: +" + cfg.lpWin() + " za wygraną / +" + cfg.lpLoss()
                + " za przegraną. Występ: PR <35: -2, 35–44: -1, 45–54: 0, "
                + "55–64: +1, 65–74: +2, 75+: +3. MVP +" + cfg.lpMvpBonus()
                + ", ACE od PR " + Math.round(cfg.lpAceMinPr()) + ": +" + cfg.lpAceBonus()
                + " (te dwa bonusy nie łączą się). Najlepsze KDA +" + cfg.lpBestKdaBonus()
                + ", perfect KDA +" + cfg.lpPerfectKdaBonus()
                + "; bonusy KDA łączą się z pozostałymi.";
        List<PrMetric> prMetrics = prDetail == null ? List.of()
                : prDetail.metrics().stream()
                .map(m -> new PrMetric(m.key(), m.value(), m.average(), m.normalized(),
                        m.weight(), m.points()))
                .toList();
        return new LpBreakdown(c, total, formula, prMetrics);
    }

    private record ParticipantScoring(LpBreakdown lpBreakdown, boolean bestKda,
                                      boolean perfectKda) {}

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
