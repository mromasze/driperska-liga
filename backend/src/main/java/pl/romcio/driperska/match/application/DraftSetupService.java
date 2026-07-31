package pl.romcio.driperska.match.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.InvalidTransitionException;
import pl.romcio.driperska.match.api.DrawLobbyDtos.CaptainVoteView;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DraftSetupSideView;
import pl.romcio.driperska.match.api.DrawLobbyDtos.DraftSetupView;
import pl.romcio.driperska.match.application.draft.DraftState;
import pl.romcio.driperska.match.application.draft.DraftStateStore;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/**
 * What each team settles between "squad confirmed" and the first ban.
 *
 * <p>Three things, in this order: the five players vote one of themselves in as captain, that captain
 * arranges the order the team will pick in, and then declares the team ready. The draft starts by
 * itself the moment both teams are ready — nobody has to wait for an admin, though an admin can force
 * any of it (or skip the whole phase with the old "start draft" button).
 *
 * <p>Nothing here is mandatory. A team that never votes gets a random captain and a random order at
 * start, which is exactly how every draft worked before this phase existed.
 */
@Service
public class DraftSetupService {

    /** Five players per side, so three votes are a majority and settle it outright. */
    private static final int VOTES_TO_DECIDE = 3;

    private final MatchService matchService;
    private final DraftStateStore store;
    private final DraftService draftService;
    private final PlayerRepository playerRepository;
    private final MatchEventRecorder eventRecorder;
    private final Random random = new Random();

    public DraftSetupService(MatchService matchService, DraftStateStore store,
                            DraftService draftService, PlayerRepository playerRepository,
                            MatchEventRecorder eventRecorder) {
        this.matchService = matchService;
        this.store = store;
        this.draftService = draftService;
        this.playerRepository = playerRepository;
        this.eventRecorder = eventRecorder;
    }

    // --- player actions ----------------------------------------------------

    /** Vote a team-mate (or yourself) in as captain. Changing your mind is fine until it is decided. */
    @Transactional
    public void voteCaptain(UUID matchId, UUID accountId, UUID candidateId) {
        Match match = requireSetupPhase(matchId);
        UUID voterId = playerId(accountId);
        MatchParticipant voter = participant(match, voterId);
        MatchParticipant candidate = participant(match, candidateId);
        if (voter.getSide() != candidate.getSide()) {
            throw new BusinessRuleException("Kapitana wybiera się we własnej drużynie");
        }
        Side side = voter.getSide();
        DraftState state = store.findOrNew(matchId);
        if (state.setup.captainFor(side) != null) {
            throw new BusinessRuleException("Kapitan tej drużyny jest już wybrany");
        }
        state.setup.votesFor(side).put(voterId, candidateId);
        resolveCaptain(match, state, side);
        store.save(matchId, state);
    }

    /**
     * The captain arranges who picks first, second, … in their team. Must list exactly the five of
     * them; an empty list gives the arrangement back to chance.
     */
    @Transactional
    public void setOrder(UUID matchId, UUID accountId, List<UUID> order) {
        Match match = requireSetupPhase(matchId);
        UUID me = playerId(accountId);
        Side side = participant(match, me).getSide();
        DraftState state = store.findOrNew(matchId);
        requireCaptain(state, side, me);
        if (order == null || order.isEmpty()) {
            state.setup.setOrder(side, List.of());
        } else {
            List<UUID> squad = squad(match, side);
            if (order.size() != squad.size() || !new HashSet<>(order).equals(new HashSet<>(squad))) {
                throw new BusinessRuleException(
                        "Kolejność musi zawierać dokładnie tych pięciu graczy z Twojej drużyny");
            }
            state.setup.setOrder(side, order);
        }
        store.save(matchId, state);
    }

    /**
     * The captain declares the team ready (or takes it back). Both teams ready starts the draft
     * immediately — that is the whole point of the phase.
     */
    @Transactional
    public void setReady(UUID matchId, UUID accountId, boolean ready) {
        Match match = requireSetupPhase(matchId);
        UUID me = playerId(accountId);
        Side side = participant(match, me).getSide();
        DraftState state = store.findOrNew(matchId);
        requireCaptain(state, side, me);
        applyReady(match, state, side, ready, me);
    }

    // --- admin overrides ---------------------------------------------------

    /** Admin appoints a captain — for the team that cannot agree, or is one player short of voting. */
    @Transactional
    public void adminSetCaptain(UUID matchId, Side side, UUID playerId, UUID actor) {
        Match match = requireSetupPhase(matchId);
        MatchParticipant target = participant(match, playerId);
        if (target.getSide() != side) {
            throw new BusinessRuleException("Ten gracz nie jest w tej drużynie");
        }
        DraftState state = store.findOrNew(matchId);
        state.setup.setCaptain(side, playerId);
        store.save(matchId, state);
        eventRecorder.record(matchId, MatchEventType.DRAFT_STARTED, actor,
                Map.of("adminCaptain", playerId.toString(), "side", side.name()));
    }

    /** Admin marks a team ready — same effect as the captain pressing it, including the auto-start. */
    @Transactional
    public void adminSetReady(UUID matchId, Side side, boolean ready, UUID actor) {
        Match match = requireSetupPhase(matchId);
        DraftState state = store.findOrNew(matchId);
        applyReady(match, state, side, ready, actor);
    }

    /** Admin wipes the votes, captains, orders and readiness so a team can start over. */
    @Transactional
    public void adminReset(UUID matchId) {
        requireSetupPhase(matchId);
        DraftState state = store.findOrNew(matchId);
        state.setup = new DraftState.Setup();
        store.save(matchId, state);
    }

    // --- view --------------------------------------------------------------

    /**
     * The setup, or null once it stops being relevant. Present only in {@code DRAFT_READY}: before that
     * there are no confirmed teams to captain, and after it the draft board takes over the screen.
     */
    @Transactional(readOnly = true)
    public DraftSetupView view(Match match) {
        if (match.getStatus() != MatchStatus.DRAFT_READY) return null;
        DraftState.Setup setup = store.find(match.getId()).map(state -> state.setup)
                .orElseGet(DraftState.Setup::new);
        return new DraftSetupView(
                sideView(match, setup, Side.BLUE),
                sideView(match, setup, Side.RED),
                VOTES_TO_DECIDE);
    }

    private DraftSetupSideView sideView(Match match, DraftState.Setup setup, Side side) {
        List<UUID> squad = squad(match, side);
        Map<UUID, Integer> tally = tally(setup.votesFor(side), squad);
        List<CaptainVoteView> votes = squad.stream()
                .map(playerId -> new CaptainVoteView(playerId, tally.getOrDefault(playerId, 0)))
                .toList();
        List<UUID> order = setup.orderFor(side);
        boolean orderValid = order.size() == squad.size()
                && new HashSet<>(order).equals(new HashSet<>(squad));
        return new DraftSetupSideView(
                setup.captainFor(side),
                votes,
                setup.votesFor(side).size(),
                squad.size(),
                orderValid ? List.copyOf(order) : List.of(),
                setup.readyFor(side));
    }

    // --- internals ---------------------------------------------------------

    private void applyReady(Match match, DraftState state, Side side, boolean ready, UUID actor) {
        if (ready && state.setup.captainFor(side) == null) {
            throw new BusinessRuleException("Najpierw wybierzcie kapitana");
        }
        state.setup.setReady(side, ready);
        store.save(match.getId(), state);
        if (state.setup.bothReady()) {
            // Both sides declared themselves ready, so there is nothing left to wait for.
            draftService.start(match, actor);
        }
    }

    /**
     * Turns the votes into a captain once the answer is beyond doubt: three of five settles it
     * immediately, and when everyone has voted the leader takes it (a tie is broken by a coin toss —
     * five voters can deadlock 2-2-1, and stalling the whole draft over it would be worse).
     */
    private void resolveCaptain(Match match, DraftState state, Side side) {
        List<UUID> squad = squad(match, side);
        Map<UUID, Integer> tally = tally(state.setup.votesFor(side), squad);
        int best = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        boolean everyoneVoted = state.setup.votesFor(side).size() >= squad.size();
        if (best < VOTES_TO_DECIDE && !everyoneVoted) return;
        List<UUID> leaders = tally.entrySet().stream()
                .filter(entry -> entry.getValue() == best && best > 0)
                .map(Map.Entry::getKey)
                .toList();
        if (leaders.isEmpty()) return;
        UUID captain = leaders.size() == 1 ? leaders.get(0)
                : leaders.get(random.nextInt(leaders.size()));
        state.setup.setCaptain(side, captain);
    }

    /** Votes per candidate, counting only candidates still in the squad. */
    private static Map<UUID, Integer> tally(Map<UUID, UUID> votes, List<UUID> squad) {
        Set<UUID> eligible = new HashSet<>(squad);
        Map<UUID, Integer> tally = new LinkedHashMap<>();
        votes.forEach((voter, candidate) -> {
            if (eligible.contains(candidate)) tally.merge(candidate, 1, Integer::sum);
        });
        return tally;
    }

    private Match requireSetupPhase(UUID matchId) {
        Match match = matchService.get(matchId);
        if (match.getStatus() != MatchStatus.DRAFT_READY) {
            throw new InvalidTransitionException(
                    "Kapitanów i kolejność ustala się po zatwierdzeniu składu, przed startem draftu");
        }
        if (match.getParticipants().size() != MatchService.REQUIRED_POOL_SIZE) {
            throw new BusinessRuleException("Draft wymaga pełnego składu 10 graczy");
        }
        return match;
    }

    private static void requireCaptain(DraftState state, Side side, UUID playerId) {
        UUID captain = state.setup.captainFor(side);
        if (captain == null) {
            throw new BusinessRuleException("Najpierw wybierzcie kapitana");
        }
        if (!captain.equals(playerId)) {
            throw new BusinessRuleException("To może ustawić tylko kapitan drużyny");
        }
    }

    private static List<UUID> squad(Match match, Side side) {
        List<UUID> ids = new ArrayList<>();
        match.getParticipants().stream().filter(p -> p.getSide() == side)
                .forEach(p -> ids.add(p.getPlayerId()));
        return ids;
    }

    private UUID playerId(UUID accountId) {
        return playerRepository.findByAccountId(accountId).map(Player::getId)
                .orElseThrow(() -> new BusinessRuleException("Konto nie jest połączone z graczem"));
    }

    private static MatchParticipant participant(Match match, UUID playerId) {
        return match.getParticipants().stream().filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Gracz nie należy do tego meczu"));
    }

}
