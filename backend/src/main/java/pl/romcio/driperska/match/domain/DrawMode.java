package pl.romcio.driperska.match.domain;

public enum DrawMode {
    /** Pure random shuffle of the pool. */
    PURE_RANDOM,
    /** Minimise the MMR gap between teams while respecting roles. */
    BALANCED,
    /** Admin assigns sides manually. */
    MANUAL
}
