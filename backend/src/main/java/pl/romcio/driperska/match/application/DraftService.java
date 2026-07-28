package pl.romcio.driperska.match.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.InvalidTransitionException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DraftStepView;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DraftSwapView;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DraftView;
import pl.romcio.driperska.match.application.draft.DraftState;
import pl.romcio.driperska.match.application.draft.DraftState.StepType;
import pl.romcio.driperska.match.application.draft.DraftState.SwapType;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchDraft;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchDraftRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/**
 * Internal LoL-style champion draft used when Riot API support is off. Runs the canonical
 * tournament pick/ban order: each team's bans are made by its captain, each pick is locked by the
 * individual player, with a per-step timer that auto-assigns a random champion on timeout.
 */
@Service
public class DraftService {

    private final MatchService matchService;
    private final MatchDraftRepository draftRepository;
    private final ChampionRepository championRepository;
    private final PlayerRepository playerRepository;
    private final MatchEventRecorder eventRecorder;
    private final ObjectMapper objectMapper;
    private final int stepSeconds;
    private final Random random = new Random();

    public DraftService(MatchService matchService, MatchDraftRepository draftRepository,
                        ChampionRepository championRepository, PlayerRepository playerRepository,
                        MatchEventRecorder eventRecorder, ObjectMapper objectMapper,
                        @Value("${app.draft.step-seconds:30}") int stepSeconds) {
        this.matchService = matchService;
        this.draftRepository = draftRepository;
        this.championRepository = championRepository;
        this.playerRepository = playerRepository;
        this.eventRecorder = eventRecorder;
        this.objectMapper = objectMapper;
        this.stepSeconds = stepSeconds;
    }

    // --- lifecycle ---------------------------------------------------------

    /** Start (or restart) the draft for a match that has drawn teams. Called from DrawService.confirm. */
    @Transactional
    public void start(Match match, UUID actor) {
        if (match.getParticipants().size() != MatchService.REQUIRED_POOL_SIZE) {
            throw new BusinessRuleException("Draft wymaga pełnego składu 10 graczy");
        }
        // Clear any champion assignments from a previous draft, then build fresh state.
        match.getParticipants().forEach(p -> p.setChampionId(null));

        DraftState state = new DraftState();
        state.sequence = DraftState.tournamentSequence();
        state.currentIndex = 0;
        state.deadline = Instant.now().plusSeconds(stepSeconds);
        // Draft order per team = random shuffle; the first in the list is the captain (on top).
        // Positions/roles play no part in the draft order — picks simply flow top→bottom.
        state.blueOrder = draftOrder(match, Side.BLUE);
        state.redOrder = draftOrder(match, Side.RED);
        state.blueCaptain = state.blueOrder.isEmpty() ? null : state.blueOrder.get(0);
        state.redCaptain = state.redOrder.isEmpty() ? null : state.redOrder.get(0);

        if (match.getStatus() != MatchStatus.DRAFTING) {
            match.transitionTo(MatchStatus.DRAFTING);
        }
        persist(match.getId(), state);
        eventRecorder.record(match.getId(), MatchEventType.DRAFT_STARTED, actor,
                java.util.Map.of("blueCaptain", state.blueCaptain, "redCaptain", state.redCaptain));
    }

    /** Admin kicks off the draft once the squad is confirmed (DRAFT_READY). */
    @Transactional
    public void startDraft(UUID matchId, UUID actor) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.DRAFT_READY && match.getStatus() != MatchStatus.TEAMS_DRAWN) {
            throw new InvalidTransitionException("Draft można rozpocząć dopiero po zatwierdzeniu składu");
        }
        start(match, actor);
    }

    /** Admin pause — freeze the step timer (e.g. to sort out a Discord lobby). */
    @Transactional
    public void pause(UUID matchId) {
        requireDrafting(matchId);
        DraftState state = load(matchId);
        if (state.paused) return;
        long remaining = state.deadline == null ? stepSeconds
                : Math.max(1, java.time.Duration.between(Instant.now(), state.deadline).getSeconds());
        state.pausedRemainingSeconds = (int) remaining;
        state.deadline = null; // scheduler skips null deadlines → no auto-assign while paused
        state.paused = true;
        persist(matchId, state);
    }

    @Transactional
    public void resume(UUID matchId) {
        requireDrafting(matchId);
        DraftState state = load(matchId);
        if (!state.paused) return;
        state.deadline = Instant.now().plusSeconds(state.pausedRemainingSeconds > 0 ? state.pausedRemainingSeconds : stepSeconds);
        state.paused = false;
        persist(matchId, state);
    }

    /** Admin re-roll of the whole draft. */
    @Transactional
    public void reset(UUID matchId, UUID actor) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.DRAFTING && match.getStatus() != MatchStatus.DRAFTED) {
            throw new InvalidTransitionException("Reset draftu możliwy tylko w trakcie lub po draftcie");
        }
        start(match, actor);
        eventRecorder.record(matchId, MatchEventType.DRAFT_RESET, actor, java.util.Map.of());
    }

    // --- player actions ----------------------------------------------------

    @Transactional
    public void ban(UUID matchId, UUID accountId, int championId) {
        Match match = requireDrafting(matchId);
        DraftState state = load(matchId);
        requireNotPaused(state);
        DraftState.Step step = requireStep(state, StepType.BAN);
        UUID playerId = player(accountId).getId();
        if (!playerId.equals(state.captainFor(step.side))) {
            throw new BusinessRuleException("Bany wykonuje kapitan drużyny");
        }
        requireAvailable(match, state, championId);
        state.bansFor(step.side).add(championId);
        advance(match, state);
        persist(matchId, state);
    }

    @Transactional
    public void pick(UUID matchId, UUID accountId, int championId) {
        Match match = requireDrafting(matchId);
        DraftState state = load(matchId);
        requireNotPaused(state);
        DraftState.Step step = requireStep(state, StepType.PICK);
        UUID playerId = player(accountId).getId();
        MatchParticipant expected = currentPicker(match, state, step.side);
        if (expected == null || !expected.getPlayerId().equals(playerId)) {
            throw new BusinessRuleException("Teraz nie jest Twoja kolej wyboru");
        }
        requireAvailable(match, state, championId);
        expected.setChampionId(championId);
        advance(match, state);
        persist(matchId, state);
    }

    /**
     * Live pre-selection of the player on the clock. Broadcast to both teams so the whole lobby sees
     * what is about to be locked; a null champion clears it. Only the player whose turn it is may set
     * a hover, and it never changes the draft itself.
     */
    @Transactional
    public void hover(UUID matchId, UUID accountId, Integer championId) {
        Match match = requireDrafting(matchId);
        DraftState state = load(matchId);
        DraftState.Step step = state.current();
        if (step == null) return;
        UUID playerId = player(accountId).getId();
        UUID onClock = step.type == StepType.BAN
                ? state.captainFor(step.side)
                : pickerId(match, state, step.side);
        if (onClock == null || !onClock.equals(playerId)) {
            throw new BusinessRuleException("Podświetlać postać może tylko gracz, do którego należy tura");
        }
        if (championId != null && !available(match, state).contains(championId)) {
            throw new BusinessRuleException("Ta postać jest już zbanowana lub wybrana");
        }
        state.hoverChampionId = championId;
        state.hoverPlayerId = championId == null ? null : playerId;
        persist(matchId, state);
    }

    /**
     * Admin correction of a single player's champion, for the common case of somebody locking in the
     * wrong one. Allowed while the draft is running and after it finished; the pick order is derived
     * from the step pointer, so this never moves whose turn it is. The champion must still be free
     * (its previous owner's slot is what gets overwritten).
     */
    @Transactional
    public void adminSetChampion(UUID matchId, UUID playerId, Integer championId, UUID actor) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.DRAFTING && match.getStatus() != MatchStatus.DRAFTED) {
            throw new InvalidTransitionException(
                    "Postać można podmienić tylko w trakcie draftu lub po jego zakończeniu");
        }
        MatchParticipant target = participant(match, playerId);
        DraftState state = load(matchId);
        if (championId != null && !championId.equals(target.getChampionId())) {
            if (championRepository.findById(championId).isEmpty()) {
                throw new BusinessRuleException("Nie ma takiej postaci");
            }
            if (unavailable(match, state).contains(championId)) {
                throw new BusinessRuleException("Ta postać jest już zbanowana lub wybrana");
            }
        }
        target.setChampionId(championId);
        persist(matchId, state);
        eventRecorder.record(matchId, MatchEventType.DRAFT_SWAP, actor,
                java.util.Map.of("adminOverride", true, "playerId", playerId,
                        "championId", championId == null ? "" : championId));
    }

    // --- swaps (post-draft) -----------------------------------------------

    @Transactional
    public void swapRequest(UUID matchId, UUID accountId, UUID targetPlayerId, SwapType type) {
        Match match = requireDrafted(matchId);
        DraftState state = load(matchId);
        UUID fromPlayerId = player(accountId).getId();
        MatchParticipant from = participant(match, fromPlayerId);
        MatchParticipant to = participant(match, targetPlayerId);
        if (from.getSide() != to.getSide()) {
            throw new BusinessRuleException("Zamiana możliwa tylko w obrębie własnej drużyny");
        }
        if (fromPlayerId.equals(targetPlayerId)) {
            throw new BusinessRuleException("Nie można zamienić się ze sobą");
        }
        state.swaps.removeIf(s -> involves(s, fromPlayerId, targetPlayerId) && s.type == type);
        state.swaps.add(new DraftState.Swap(UUID.randomUUID(), fromPlayerId, targetPlayerId, type));
        persist(matchId, state);
    }

    @Transactional
    public void swapAccept(UUID matchId, UUID accountId, UUID swapId) {
        Match match = requireDrafted(matchId);
        DraftState state = load(matchId);
        UUID me = player(accountId).getId();
        DraftState.Swap swap = state.swaps.stream().filter(s -> s.id.equals(swapId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Zaproszenie do zamiany wygasło"));
        if (!swap.toPlayerId.equals(me)) {
            throw new BusinessRuleException("Tylko zaproszony gracz może zaakceptować zamianę");
        }
        MatchParticipant a = participant(match, swap.fromPlayerId);
        MatchParticipant b = participant(match, swap.toPlayerId);
        if (swap.type == SwapType.POSITION) {
            var role = a.getRole();
            a.setRole(b.getRole());
            b.setRole(role);
        } else {
            Integer champ = a.getChampionId();
            a.setChampionId(b.getChampionId());
            b.setChampionId(champ);
        }
        state.swaps.removeIf(s -> involves(s, swap.fromPlayerId, swap.toPlayerId));
        persist(matchId, state);
        eventRecorder.record(matchId, MatchEventType.DRAFT_SWAP, accountId,
                java.util.Map.of("type", swap.type.name(), "a", swap.fromPlayerId, "b", swap.toPlayerId));
    }

    @Transactional
    public void swapCancel(UUID matchId, UUID accountId, UUID swapId) {
        requireDrafted(matchId);
        DraftState state = load(matchId);
        UUID me = player(accountId).getId();
        state.swaps.removeIf(s -> s.id.equals(swapId)
                && (s.fromPlayerId.equals(me) || s.toPlayerId.equals(me)));
        persist(matchId, state);
    }

    // --- timeout -----------------------------------------------------------

    /**
     * Auto-resolve the current step when the clock runs out (called by the scheduler). The player's
     * own pre-selection wins if they had one highlighted but never pressed lock-in; otherwise a random
     * available champion is used. Either way the slot ends up filled and therefore visible to everyone
     * the moment the timer expires — a step must never be left uncovered.
     */
    @Transactional
    public void resolveExpired(UUID matchId) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.DRAFTING) return;
        DraftState state = load(matchId);
        DraftState.Step step = state.current();
        if (step == null || state.deadline == null || state.deadline.isAfter(Instant.now())) return;

        Set<Integer> free = available(match, state);
        Integer champ = state.hoverChampionId != null && free.contains(state.hoverChampionId)
                ? state.hoverChampionId
                : randomAvailable(match, state);
        if (step.type == StepType.BAN) {
            if (champ != null) state.bansFor(step.side).add(champ);
        } else {
            MatchParticipant slot = currentPicker(match, state, step.side);
            if (slot != null && champ != null) slot.setChampionId(champ);
        }
        if (champ != null) state.autoResolvedSteps.add(state.currentIndex);
        advance(match, state);
        persist(matchId, state);
    }

    // --- view --------------------------------------------------------------

    @Transactional(readOnly = true)
    public DraftView view(Match match) {
        MatchDraft draft = draftRepository.findById(match.getId()).orElse(null);
        if (draft == null) return null;
        DraftState state = deserialize(draft.getState());
        DraftState.Step current = state.current();
        List<DraftStepView> sequence = state.sequence.stream()
                .map(s -> new DraftStepView(s.side, s.type.name())).toList();
        List<DraftSwapView> swaps = state.swaps.stream()
                .map(s -> new DraftSwapView(s.id, s.fromPlayerId, s.toPlayerId, s.type.name())).toList();
        UUID currentPlayerId = null;
        if (current != null) {
            currentPlayerId = current.type == StepType.BAN
                    ? state.captainFor(current.side)
                    : pickerId(match, state, current.side);
        }
        return new DraftView(
                state.complete ? "DONE" : "DRAFTING",
                state.currentIndex, state.deadline,
                current != null ? current.side : null,
                current != null ? current.type.name() : null,
                state.blueCaptain, state.redCaptain, currentPlayerId, state.paused,
                List.copyOf(state.blueOrder), List.copyOf(state.redOrder),
                List.copyOf(state.blueBans), List.copyOf(state.redBans),
                sequence, swaps,
                state.hoverChampionId, state.hoverPlayerId,
                List.copyOf(state.autoResolvedSteps), stepSeconds);
    }

    // --- helpers -----------------------------------------------------------

    private void advance(Match match, DraftState state) {
        state.clearHover();
        state.currentIndex++;
        if (state.currentIndex >= state.sequence.size()) {
            state.complete = true;
            state.deadline = null;
            match.transitionTo(MatchStatus.DRAFTED);
            eventRecorder.record(match.getId(), MatchEventType.DRAFT_COMPLETED, match.getCreatedBy(),
                    java.util.Map.of());
        } else {
            state.deadline = Instant.now().plusSeconds(stepSeconds);
        }
    }

    private Match requireDrafting(UUID matchId) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.DRAFTING) {
            throw new InvalidTransitionException("Draft nie jest aktywny");
        }
        return match;
    }

    private Match requireDrafted(UUID matchId) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.DRAFTED) {
            throw new InvalidTransitionException("Zamiany możliwe dopiero po zakończeniu draftu");
        }
        return match;
    }

    private void requireNotPaused(DraftState state) {
        if (state.paused) {
            throw new BusinessRuleException("Draft jest wstrzymany przez admina");
        }
    }

    private DraftState.Step requireStep(DraftState state, StepType expected) {
        DraftState.Step step = state.current();
        if (step == null || step.type != expected) {
            throw new BusinessRuleException("Teraz nie jest tura na " + (expected == StepType.BAN ? "ban" : "pick"));
        }
        return step;
    }

    private void requireAvailable(Match match, DraftState state, int championId) {
        if (!available(match, state).contains(championId)) {
            throw new BusinessRuleException("Ta postać jest już zbanowana lub wybrana");
        }
    }

    private Set<Integer> unavailable(Match match, DraftState state) {
        Set<Integer> used = new HashSet<>();
        used.addAll(state.blueBans);
        used.addAll(state.redBans);
        match.getParticipants().forEach(p -> {
            if (p.getChampionId() != null) used.add(p.getChampionId());
        });
        return used;
    }

    private Set<Integer> available(Match match, DraftState state) {
        Set<Integer> all = new HashSet<>();
        championRepository.findAll().forEach(c -> all.add(c.getId()));
        all.removeAll(unavailable(match, state));
        return all;
    }

    private Integer randomAvailable(Match match, DraftState state) {
        List<Integer> pool = new ArrayList<>(available(match, state));
        if (pool.isEmpty()) return null;
        return pool.get(random.nextInt(pool.size()));
    }

    /** A random top→bottom draft order for a side (element 0 = captain). */
    private List<UUID> draftOrder(Match match, Side side) {
        List<UUID> ids = new ArrayList<>(match.getParticipants().stream()
                .filter(p -> p.getSide() == side).map(MatchParticipant::getPlayerId).toList());
        Collections.shuffle(ids, random);
        return ids;
    }

    /**
     * The participant whose turn it is to pick: the Nth pick on a side goes to order[N] (top→bottom).
     * N comes from the step pointer, not from how many champions are currently assigned — otherwise
     * an admin fixing somebody's champion, or a step that timed out with no champion available, would
     * silently shift whose turn it is.
     */
    private MatchParticipant currentPicker(Match match, DraftState state, Side side) {
        List<UUID> order = state.orderFor(side);
        int consumed = state.picksConsumed(side);
        if (consumed >= order.size()) return null;
        UUID pid = order.get(consumed);
        return match.getParticipants().stream()
                .filter(p -> p.getPlayerId().equals(pid)).findFirst().orElse(null);
    }

    private UUID pickerId(Match match, DraftState state, Side side) {
        MatchParticipant p = currentPicker(match, state, side);
        return p == null ? null : p.getPlayerId();
    }

    private static boolean involves(DraftState.Swap s, UUID a, UUID b) {
        return (s.fromPlayerId.equals(a) && s.toPlayerId.equals(b))
                || (s.fromPlayerId.equals(b) && s.toPlayerId.equals(a));
    }

    private Player player(UUID accountId) {
        return playerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessRuleException("Konto nie jest połączone z graczem"));
    }

    private MatchParticipant participant(Match match, UUID playerId) {
        return match.getParticipants().stream().filter(p -> p.getPlayerId().equals(playerId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("Gracz nie należy do tego meczu"));
    }

    private void persist(UUID matchId, DraftState state) {
        String json = serialize(state);
        MatchDraft draft = draftRepository.findById(matchId).orElse(null);
        if (draft == null) {
            draftRepository.save(new MatchDraft(matchId, json, state.deadline));
        } else {
            draft.update(json, state.deadline);
            draftRepository.save(draft);
        }
    }

    private DraftState load(UUID matchId) {
        MatchDraft draft = draftRepository.findById(matchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Draft", matchId));
        return deserialize(draft.getState());
    }

    private String serialize(DraftState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalStateException("Nie udało się zapisać stanu draftu", ex);
        }
    }

    private DraftState deserialize(String json) {
        try {
            return objectMapper.readValue(json, DraftState.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Nie udało się odczytać stanu draftu", ex);
        }
    }
}
