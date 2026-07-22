package pl.romcio.driperska.common.domain;

/** Team side in a match, mirroring the in-game blue/red sides. */
public enum Side {
    BLUE,
    RED;

    public Side opposite() {
        return this == BLUE ? RED : BLUE;
    }
}
