package pl.romcio.driperska.account.domain;

/**
 * Access level of a panel account.
 *
 * <ul>
 *   <li>{@code ADMIN} — full control: manages accounts, players, seasons, draws teams, enters
 *       results, and is the only role that can approve/reject submitted results.</li>
 *   <li>{@code EDITOR} — can start matches, draw teams and enter results, but never approves;
 *       everything they submit goes to an admin for sign-off.</li>
 *   <li>{@code PLAYER} — the player panel only (profile, draft, ratings).</li>
 * </ul>
 *
 * <p>Orthogonal to this enum sits {@link Account#isModerator()}: a PLAYER carrying that flag may
 * additionally record past matches into the approval queue. It is a flag rather than a fourth role
 * because a moderator must stay a player — losing the draft and the profile would be a demotion.
 */
public enum AccountRole {
    ADMIN,
    EDITOR,
    PLAYER;

    /** Spring Security authority name (e.g. {@code ROLE_ADMIN}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
