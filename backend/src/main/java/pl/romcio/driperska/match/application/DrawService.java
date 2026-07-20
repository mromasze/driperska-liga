package pl.romcio.driperska.match.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.InvalidTransitionException;
import pl.romcio.driperska.match.domain.DrawMode;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;
import pl.romcio.driperska.ranking.application.PlayerMmrService;

/** Draws (and re-rolls) balanced or random teams for a match in DRAFT/TEAMS_DRAWN. */
@Service
public class DrawService {

    private final MatchService matchService;
    private final PlayerRepository playerRepository;
    private final PlayerMmrService mmrService;
    private final MatchEventRecorder eventRecorder;
    private final Random random = new Random();

    public DrawService(MatchService matchService,
                       PlayerRepository playerRepository,
                       PlayerMmrService mmrService,
                       MatchEventRecorder eventRecorder) {
        this.matchService = matchService;
        this.playerRepository = playerRepository;
        this.mmrService = mmrService;
        this.eventRecorder = eventRecorder;
    }

    public record Slot(UUID playerId, String nickname, Role role, double mmr, Side side) {
    }

    public record DrawResult(List<Slot> slots, double blueMmrAvg, double redMmrAvg,
                             double predictedBlueWinPct) {
    }

    @Transactional
    public DrawResult draw(UUID matchId, UUID actor) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.DRAFT && match.getStatus() != MatchStatus.TEAMS_DRAWN) {
            throw new InvalidTransitionException("Losowanie możliwe tylko przed startem gry");
        }
        List<UUID> pool = match.getPoolPlayerIds();
        Map<UUID, Player> players = new HashMap<>();
        playerRepository.findByIdIn(pool).forEach(p -> players.put(p.getId(), p));
        Map<UUID, Double> mmr = mmrService.currentMmr(match.getSeasonId(), pool);

        List<UUID> blueIds = switch (match.getDrawMode()) {
            case BALANCED -> balancedSplit(pool, mmr);
            case PURE_RANDOM, MANUAL -> randomSplit(pool);
        };

        List<MatchParticipant> participants = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        for (UUID playerId : pool) {
            Player player = players.get(playerId);
            Side side = blueIds.contains(playerId) ? Side.BLUE : Side.RED;
            Role role = player.getMainRole() != null ? player.getMainRole() : Role.MID;
            participants.add(new MatchParticipant(playerId, side, role));
            slots.add(new Slot(playerId, player.getNickname(), role, mmr.getOrDefault(playerId, 0.0), side));
        }
        match.replaceParticipants(participants);
        match.transitionTo(MatchStatus.TEAMS_DRAWN);

        double blueAvg = avg(slots, Side.BLUE);
        double redAvg = avg(slots, Side.RED);
        double predictedBlue = round1(100.0 / (1.0 + Math.pow(10, (redAvg - blueAvg) / 400.0)));

        eventRecorder.record(matchId, MatchEventType.TEAMS_DRAWN, actor, Map.of(
                "blue", blueIds,
                "mode", match.getDrawMode().name(),
                "predictedBlueWinPct", predictedBlue));
        return new DrawResult(slots, round1(blueAvg), round1(redAvg), predictedBlue);
    }

    @Transactional
    public Match confirm(UUID matchId, UUID actor) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.TEAMS_DRAWN) {
            throw new InvalidTransitionException("Najpierw wylosuj drużyny");
        }
        match.transitionTo(MatchStatus.LIVE);
        match.setStartedAt(java.time.Instant.now());
        eventRecorder.record(matchId, MatchEventType.DRAW_CONFIRMED, actor, null);
        return match;
    }

    private List<UUID> randomSplit(List<UUID> pool) {
        List<UUID> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random);
        return new ArrayList<>(shuffled.subList(0, pool.size() / 2));
    }

    /** Picks the 5-player subset whose MMR sum is closest to half the total, breaking ties randomly. */
    private List<UUID> balancedSplit(List<UUID> pool, Map<UUID, Double> mmr) {
        int n = pool.size();
        int half = n / 2;
        double total = pool.stream().mapToDouble(id -> mmr.getOrDefault(id, 0.0)).sum();

        List<int[]> combos = combinations(n, half);
        double bestDiff = Double.MAX_VALUE;
        List<int[]> best = new ArrayList<>();
        for (int[] combo : combos) {
            double sum = 0;
            for (int idx : combo) {
                sum += mmr.getOrDefault(pool.get(idx), 0.0);
            }
            double diff = Math.abs(2 * sum - total);
            if (diff < bestDiff - 1e-9) {
                bestDiff = diff;
                best.clear();
                best.add(combo);
            } else if (Math.abs(diff - bestDiff) <= 1e-9) {
                best.add(combo);
            }
        }
        int[] chosen = best.get(random.nextInt(best.size()));
        List<UUID> blue = new ArrayList<>();
        for (int idx : chosen) {
            blue.add(pool.get(idx));
        }
        return blue;
    }

    private static List<int[]> combinations(int n, int k) {
        List<int[]> result = new ArrayList<>();
        int[] combo = new int[k];
        combineRec(0, 0, n, k, combo, result);
        return result;
    }

    private static void combineRec(int start, int depth, int n, int k, int[] combo, List<int[]> result) {
        if (depth == k) {
            result.add(combo.clone());
            return;
        }
        for (int i = start; i <= n - (k - depth); i++) {
            combo[depth] = i;
            combineRec(i + 1, depth + 1, n, k, combo, result);
        }
    }

    private static double avg(List<Slot> slots, Side side) {
        return slots.stream().filter(s -> s.side() == side)
                .mapToDouble(Slot::mmr).average().orElse(0);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
