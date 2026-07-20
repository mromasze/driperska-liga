package pl.romcio.driperska.account.domain;

/**
 * Access level of a panel account.
 *
 * <ul>
 *   <li>{@code ADMIN} — full control: manages accounts, players, seasons, draws teams, enters
 *       results, and is the only role that can approve/reject submitted results.</li>
 *   <li>{@code EDITOR} — can start matches, draw teams and enter results, but never approves;
 *       everything they submit goes to an admin for sign-off.</li>
 * </ul>
 */
public enum AccountRole {
    ADMIN,
    EDITOR;

    /** Spring Security authority name (e.g. {@code ROLE_ADMIN}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
