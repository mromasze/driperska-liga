package pl.romcio.driperska.match.application.draft;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import pl.romcio.driperska.common.domain.Side;

/**
 * Serializable draft document (stored as JSON in {@code match_draft.state}). Mutable POJO with
 * public fields — Jackson round-trips it without getters/setters.
 *
 * <p>Champions that are locked as picks are written straight onto the {@code MatchParticipant}
 * rows, so this document only tracks bans, the step pointer, captains, and post-draft swap
 * requests. Availability = bans ∪ locked participant champions.
 */
public class DraftState {

    public enum StepType { BAN, PICK }
    public enum SwapType { POSITION, CHAMPION }

    public static class Step {
        public Side side;
        public StepType type;

        public Step() {}
        public Step(Side side, StepType type) { this.side = side; this.type = type; }
    }

    public static class Swap {
        public UUID id;
        public UUID fromPlayerId;
        public UUID toPlayerId;
        public SwapType type;

        public Swap() {}
        public Swap(UUID id, UUID fromPlayerId, UUID toPlayerId, SwapType type) {
            this.id = id;
            this.fromPlayerId = fromPlayerId;
            this.toPlayerId = toPlayerId;
            this.type = type;
        }
    }

    public List<Step> sequence = new ArrayList<>();
    public int currentIndex;
    public Instant deadline;
    public UUID blueCaptain;
    public UUID redCaptain;
    public List<Integer> blueBans = new ArrayList<>();
    public List<Integer> redBans = new ArrayList<>();
    /** Draft order per team, top→bottom: captain first, then the rest (random). Drives pick turns. */
    public List<UUID> blueOrder = new ArrayList<>();
    public List<UUID> redOrder = new ArrayList<>();
    public List<Swap> swaps = new ArrayList<>();
    public boolean complete;
    /** Admin pause: freezes the step timer; deadline is cleared and restored on resume. */
    public boolean paused;
    public int pausedRemainingSeconds;
    /**
     * Live pre-selection of the player on the clock ("hover"), broadcast to both teams so everyone
     * sees what is about to be banned/picked — exactly like the in-client tournament draft. Cleared
     * on every step advance.
     */
    public Integer hoverChampionId;
    public UUID hoverPlayerId;
    /**
     * Steps whose champion was assigned by the timeout scheduler rather than by a player. Used by
     * the UI to label a slot as auto-picked once the clock ran out.
     */
    public List<Integer> autoResolvedSteps = new ArrayList<>();

    public List<UUID> orderFor(Side side) {
        return side == Side.BLUE ? blueOrder : redOrder;
    }

    /**
     * How many PICK steps this side has already consumed, i.e. the index into its draft order of the
     * player currently on the clock. Derived from the step pointer rather than from how many
     * champions happen to be assigned, so an admin correcting somebody's champion mid-draft never
     * shifts whose turn it is.
     */
    public int picksConsumed(Side side) {
        int consumed = 0;
        for (int i = 0; i < currentIndex && i < sequence.size(); i++) {
            Step step = sequence.get(i);
            if (step.type == StepType.PICK && step.side == side) consumed++;
        }
        return consumed;
    }

    public void clearHover() {
        hoverChampionId = null;
        hoverPlayerId = null;
    }

    /** The canonical LoL tournament pick/ban order: 5 bans + 5 picks per team. */
    public static List<Step> tournamentSequence() {
        List<Step> s = new ArrayList<>();
        // Ban phase 1: B R B R B R (3 each)
        s.add(new Step(Side.BLUE, StepType.BAN));
        s.add(new Step(Side.RED, StepType.BAN));
        s.add(new Step(Side.BLUE, StepType.BAN));
        s.add(new Step(Side.RED, StepType.BAN));
        s.add(new Step(Side.BLUE, StepType.BAN));
        s.add(new Step(Side.RED, StepType.BAN));
        // Pick phase 1: B R R B B R
        s.add(new Step(Side.BLUE, StepType.PICK));
        s.add(new Step(Side.RED, StepType.PICK));
        s.add(new Step(Side.RED, StepType.PICK));
        s.add(new Step(Side.BLUE, StepType.PICK));
        s.add(new Step(Side.BLUE, StepType.PICK));
        s.add(new Step(Side.RED, StepType.PICK));
        // Ban phase 2: R B R B (2 each, red first)
        s.add(new Step(Side.RED, StepType.BAN));
        s.add(new Step(Side.BLUE, StepType.BAN));
        s.add(new Step(Side.RED, StepType.BAN));
        s.add(new Step(Side.BLUE, StepType.BAN));
        // Pick phase 2: R B B R
        s.add(new Step(Side.RED, StepType.PICK));
        s.add(new Step(Side.BLUE, StepType.PICK));
        s.add(new Step(Side.BLUE, StepType.PICK));
        s.add(new Step(Side.RED, StepType.PICK));
        return s;
    }

    public Step current() {
        return currentIndex >= 0 && currentIndex < sequence.size() ? sequence.get(currentIndex) : null;
    }

    public List<Integer> bansFor(Side side) {
        return side == Side.BLUE ? blueBans : redBans;
    }

    public UUID captainFor(Side side) {
        return side == Side.BLUE ? blueCaptain : redCaptain;
    }
}
