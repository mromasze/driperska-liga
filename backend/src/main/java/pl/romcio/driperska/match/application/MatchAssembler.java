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
import pl.romcio.driperska.match.api.MatchDtos.LpBreakdown;
import pl.romcio.driperska.match.api.MatchDtos.LpComponent;
import pl.romcio.driperska.match.api.MatchDtos.ParticipantResponse;
import pl.romcio.driperska.match.api.MatchDtos.PrMetric;
import pl.romcio.driperska.match.api.MatchDtos.RiotInfoResponse;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.ranking.application.ScoringConfigProvider;
import pl.romcio.driperska.ranking.domain.MatchStatsContext;
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
    private final RatingCalculator ratingCalculator;

    public MatchAssembler(MatchRepository matchRepository,
                          PlayerRepository playerRepository,
                          ChampionRepository championRepository,
                          MatchApprovalRepository approvalRepository,
                          ScoringConfigProvider scoringConfigProvider,
                          RatingCalculator ratingCalculator) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.championRepository = championRepository;
        this.approvalRepository = approvalRepository;
        this.scoringConfigProvider = scoringConfigProvider;
        this.ratingCalculator = ratingCalculator;
    }

    /** Reloads the match inside this read-only transaction so lazy collections resolve safely. */
    @Transactional(readOnly = true)
    public MatchResponse toResponse(Match detached) {
        Match match = matchRepository.findById(detached.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Match", detached.getId()));
        Map<UUID, Player> players = playersFor(match);
        Map<Integer, Champion> champions = championsFor(match);
        Map<UUID, LpBreakdown> breakdowns = lpBreakdowns(match);
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
                                              Map<Integer, Champion> champions, LpBreakdown lpBreakdown) {
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
                p.getLargestMultiKill(), p.getPerformanceRating(), p.getLpAwarded(), p.isMvp(),
                lpBreakdown);
    }

    /**
     * Explains each player's LP for a scored match — mirrors {@link pl.romcio.driperska.ranking.domain.PointsEngine}
     * so the total matches the awarded LP. Empty until the match has a winning side + ratings.
     */
    private Map<UUID, LpBreakdown> lpBreakdowns(Match match) {
        Side winning = match.getWinningSide();
        if (winning == null) return Map.of();
        ScoringConfig cfg = scoringConfigProvider.forSeason(match.getSeasonId());
        Map<UUID, RatingCalculator.PrDetail> prDetails =
                ratingCalculator.computeDetailed(toContext(match), cfg);
        // ACE = highest performance rating on the losing side.
        UUID aceId = null;
        double bestLoserPr = -1;
        for (MatchParticipant p : match.getParticipants()) {
            if (p.getSide() == winning) continue;
            double pr = p.getPerformanceRating() == null ? 0.0 : p.getPerformanceRating();
            if (pr > bestLoserPr) { bestLoserPr = pr; aceId = p.getPlayerId(); }
        }
        Map<UUID, LpBreakdown> out = new HashMap<>();
        for (MatchParticipant p : match.getParticipants()) {
            if (p.getPerformanceRating() == null && p.getLpAwarded() == null) continue;
            out.put(p.getPlayerId(), lpBreakdownFor(p, winning, cfg, p.getPlayerId().equals(aceId),
                    prDetails.get(p.getId())));
        }
        return out;
    }

    /** Same input the ranking engine scores with — keeps the shown PR math identical to the stored one. */
    private static MatchStatsContext toContext(Match match) {
        List<MatchStatsContext.ParticipantInput> inputs = new ArrayList<>();
        for (MatchParticipant p : match.getParticipants()) {
            inputs.add(new MatchStatsContext.ParticipantInput(
                    p.getId(), p.getPlayerId(), p.getSide(), p.getRole(),
                    p.getKills(), p.getDeaths(), p.getAssists(), p.getCs(), p.getGold(),
                    p.getDamageToChampions(), p.getVisionScore(), p.getLargestMultiKill()));
        }
        int duration = match.getDurationSeconds() != null ? match.getDurationSeconds() : 1800;
        return new MatchStatsContext(match.getWinningSide(), duration, inputs);
    }

    private static LpBreakdown lpBreakdownFor(MatchParticipant p, Side winning, ScoringConfig cfg,
                                              boolean isAce, RatingCalculator.PrDetail prDetail) {
        boolean won = p.getSide() == winning;
        double pr = p.getPerformanceRating() == null ? 0.0 : p.getPerformanceRating();
        List<LpComponent> c = new ArrayList<>();
        c.add(new LpComponent(won ? "Zwycięstwo" : "Porażka", won ? cfg.lpWin() : cfg.lpLoss()));
        c.add(new LpComponent("Występ (PR " + Math.round(pr) + " ÷ " + cfg.lpPerformanceDivisor() + ")",
                (int) Math.round(pr / cfg.lpPerformanceDivisor())));
        if (p.isMvp()) c.add(new LpComponent("MVP meczu", cfg.lpMvpBonus()));
        if (isAce && !won) c.add(new LpComponent("ACE przegranej drużyny", cfg.lpAceBonus()));
        if (p.getLargestMultiKill() >= 5) c.add(new LpComponent("Pentakill", cfg.lpPentaBonus()));
        else if (p.getLargestMultiKill() == 4) c.add(new LpComponent("Quadrakill", cfg.lpQuadraBonus()));
        if (p.getDeaths() == 0 && (p.getKills() + p.getAssists()) >= 1) {
            c.add(new LpComponent("Bez śmierci", cfg.lpFlawlessBonus()));
        }
        int total = Math.max(0, c.stream().mapToInt(LpComponent::points).sum());
        String formula = "Baza: +" + cfg.lpWin() + " za wygraną / +" + cfg.lpLoss()
                + " za przegraną. Występ: zaokrąglone PR ÷ " + cfg.lpPerformanceDivisor()
                + ". Bonusy: MVP +" + cfg.lpMvpBonus() + ", ACE +" + cfg.lpAceBonus()
                + ", penta +" + cfg.lpPentaBonus() + ", quadra +" + cfg.lpQuadraBonus()
                + ", bez śmierci +" + cfg.lpFlawlessBonus() + ". Minimum 0 LP.";
        List<PrMetric> prMetrics = prDetail == null ? List.of()
                : prDetail.metrics().stream()
                .map(m -> new PrMetric(m.key(), m.value(), m.average(), m.normalized(),
                        m.weight(), m.points()))
                .toList();
        return new LpBreakdown(c, total, formula, prMetrics);
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
