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
    public List<Swap> swaps = new ArrayList<>();
    public boolean complete;

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
