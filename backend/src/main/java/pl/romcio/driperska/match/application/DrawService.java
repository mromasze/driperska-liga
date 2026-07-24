package pl.romcio.driperska.match.application;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.InvalidTransitionException;
import pl.romcio.driperska.common.settings.AppSettingService;
import pl.romcio.driperska.integration.riot.TournamentMatchService;
import pl.romcio.driperska.match.domain.*;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;
import pl.romcio.driperska.ranking.application.PlayerMmrService;

@Service
public class DrawService {
    private final MatchService matchService;
    private final PlayerRepository playerRepository;
    private final PlayerMmrService mmrService;
    private final MatchEventRecorder eventRecorder;
    private final TournamentMatchService tournamentMatchService;
    private final AppSettingService settings;
    private final Random random = new Random();

    public DrawService(MatchService matchService, PlayerRepository playerRepository,
                       PlayerMmrService mmrService, MatchEventRecorder eventRecorder,
                       TournamentMatchService tournamentMatchService,
                       AppSettingService settings) {
        this.matchService = matchService;
        this.playerRepository = playerRepository;
        this.mmrService = mmrService;
        this.eventRecorder = eventRecorder;
        this.tournamentMatchService = tournamentMatchService;
        this.settings = settings;
    }

    public record Slot(UUID playerId, String nickname, Role role, double mmr, Side side) {}
    public record DrawResult(List<Slot> slots, double blueMmrAvg, double redMmrAvg,
                             double predictedBlueWinPct) {}

    /** An admin-supplied placement of one player onto a side and role (manual team building). */
    public record ManualSlot(UUID playerId, Side side, Role role) {}

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

        List<UUID> firstTeam = switch (match.getDrawMode()) {
            case BALANCED -> balancedSplit(pool, mmr);
            case PURE_RANDOM, MANUAL -> randomSplit(pool);
        };
        // Team composition and map side are separate random choices.
        Set<UUID> blueIds = new HashSet<>();
        if (random.nextBoolean()) {
            blueIds.addAll(firstTeam);
        } else {
            pool.stream().filter(id -> !firstTeam.contains(id)).forEach(blueIds::add);
        }

        List<MatchParticipant> participants = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        for (UUID playerId : pool) {
            Player player = players.get(playerId);
            Side side = blueIds.contains(playerId) ? Side.BLUE : Side.RED;
            Role role = player.getMainRole() != null ? player.getMainRole() : Role.MID;
            participants.add(new MatchParticipant(playerId, side, role));
            slots.add(new Slot(playerId, player.getNickname(), role, mmr.getOrDefault(playerId, 0.0), side));
        }
        return finalizeDraw(match, matchId, participants, slots, actor);
    }

    /**
     * Applies an admin-built roster (exactly 5 per side, one of each role per side) without any
     * randomness. Used by the MANUAL draw mode / the manual team builder.
     */
    @Transactional
    public DrawResult manualDraw(UUID matchId, UUID actor, List<ManualSlot> assignment) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.DRAFT && match.getStatus() != MatchStatus.TEAMS_DRAWN) {
            throw new InvalidTransitionException("Ręczne ułożenie drużyn możliwe tylko przed startem gry");
        }
        List<UUID> pool = match.getPoolPlayerIds();
        validateManual(assignment, pool);
        Map<UUID, Player> players = new HashMap<>();
        playerRepository.findByIdIn(pool).forEach(p -> players.put(p.getId(), p));
        Map<UUID, Double> mmr = mmrService.currentMmr(match.getSeasonId(), pool);

        List<MatchParticipant> participants = new ArrayList<>();
        List<Slot> slots = new ArrayList<>();
        for (ManualSlot s : assignment) {
            Player player = players.get(s.playerId());
            participants.add(new MatchParticipant(s.playerId(), s.side(), s.role()));
            slots.add(new Slot(s.playerId(), player.getNickname(), s.role(),
                    mmr.getOrDefault(s.playerId(), 0.0), s.side()));
        }
        return finalizeDraw(match, matchId, participants, slots, actor);
    }

    private void validateManual(List<ManualSlot> assignment, List<UUID> pool) {
        if (assignment == null || assignment.size() != pool.size()) {
            throw new BusinessRuleException(
                    "Ręczny skład musi obejmować dokładnie %d graczy".formatted(pool.size()));
        }
        Set<UUID> assignedIds = assignment.stream().map(ManualSlot::playerId)
                .collect(Collectors.toSet());
        if (assignedIds.size() != assignment.size()) {
            throw new BusinessRuleException("Gracz nie może być przypisany dwukrotnie");
        }
        if (!assignedIds.equals(new HashSet<>(pool))) {
            throw new BusinessRuleException("Ręczny skład musi zawierać dokładnie graczy z puli meczu");
        }
        for (Side side : Side.values()) {
            List<ManualSlot> onSide = assignment.stream().filter(s -> s.side() == side).toList();
            if (onSide.size() != pool.size() / 2) {
                throw new BusinessRuleException(
                        "Każda drużyna musi mieć dokładnie %d graczy".formatted(pool.size() / 2));
            }
            long distinctRoles = onSide.stream().map(ManualSlot::role).distinct().count();
            if (distinctRoles != onSide.size()) {
                throw new BusinessRuleException("Każda rola w drużynie może wystąpić tylko raz");
            }
        }
    }

    /** Persists a computed roster, advances to TEAMS_DRAWN and records the audit event + balance. */
    private DrawResult finalizeDraw(Match match, UUID matchId, List<MatchParticipant> participants,
                                    List<Slot> slots, UUID actor) {
        Set<UUID> blueIds = slots.stream().filter(s -> s.side() == Side.BLUE)
                .map(Slot::playerId).collect(Collectors.toCollection(HashSet::new));
        match.replaceParticipants(participants);
        match.advanceDrawRound();
        match.transitionTo(MatchStatus.TEAMS_DRAWN);
        match.setTeamsDrawnAt(java.time.Instant.now());

        double blueAvg = avg(slots, Side.BLUE);
        double redAvg = avg(slots, Side.RED);
        double predictedBlue = round1(100.0 / (1.0 + Math.pow(10, (redAvg - blueAvg) / 400.0)));
        eventRecorder.record(matchId, MatchEventType.TEAMS_DRAWN, actor, Map.of(
                "blue", blueIds, "round", match.getDrawRound(),
                "mode", match.getDrawMode().name(), "predictedBlueWinPct", predictedBlue));
        return new DrawResult(slots, round1(blueAvg), round1(redAvg), predictedBlue);
    }

    @Transactional
    public Match confirm(UUID matchId, UUID actor) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.TEAMS_DRAWN) {
            throw new InvalidTransitionException("Najpierw wylosuj drużyny");
        }
        // Riot on → create the tournament lobby + join code. Riot off → confirm the squad and wait
        // for the admin to start the draft (so everyone can move to a Discord lobby and talk first).
        if (settings.isRiotEnabled()) {
            return tournamentMatchService.createLobby(matchId, actor);
        }
        match.transitionTo(MatchStatus.DRAFT_READY);
        eventRecorder.record(matchId, MatchEventType.DRAW_CONFIRMED, actor,
                java.util.Map.of("draftReady", true));
        return match;
    }

    private List<UUID> randomSplit(List<UUID> pool) {
        List<UUID> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        return new ArrayList<>(shuffled.subList(0, pool.size() / 2));
    }

    private List<UUID> balancedSplit(List<UUID> pool, Map<UUID, Double> mmr) {
        int n = pool.size(), half = n / 2;
        double total = pool.stream().mapToDouble(id -> mmr.getOrDefault(id, 0.0)).sum();
        List<int[]> combos = combinations(n, half), best = new ArrayList<>();
        double bestDiff = Double.MAX_VALUE;
        for (int[] combo : combos) {
            double sum = 0;
            for (int idx : combo) sum += mmr.getOrDefault(pool.get(idx), 0.0);
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
        List<UUID> result = new ArrayList<>();
        for (int idx : chosen) result.add(pool.get(idx));
        return result;
    }

    private static List<int[]> combinations(int n, int k) {
        List<int[]> result = new ArrayList<>();
        combineRec(0, 0, n, k, new int[k], result);
        return result;
    }
    private static void combineRec(int start, int depth, int n, int k, int[] combo, List<int[]> result) {
        if (depth == k) { result.add(combo.clone()); return; }
        for (int i = start; i <= n - (k - depth); i++) {
            combo[depth] = i;
            combineRec(i + 1, depth + 1, n, k, combo, result);
        }
    }
    private static double avg(List<Slot> slots, Side side) {
        return slots.stream().filter(s -> s.side() == side).mapToDouble(Slot::mmr).average().orElse(0);
    }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
}