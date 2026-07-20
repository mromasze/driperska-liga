package pl.romcio.driperska.common.security;

import java.util.UUID;

/** Lightweight principal placed in the security context from the access token. */
public record AuthenticatedAccount(UUID accountId, String username, String role) {

    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role);
    }
}
