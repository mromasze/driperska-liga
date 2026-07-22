package pl.romcio.driperska.common.security;

import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import pl.romcio.driperska.common.error.ResourceNotFoundException;

/** Convenience accessor for the currently authenticated account. */
public final class CurrentAccount {

    private CurrentAccount() {
    }

    public static Optional<AuthenticatedAccount> optional() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedAccount account) {
            return Optional.of(account);
        }
        return Optional.empty();
    }

    public static AuthenticatedAccount require() {
        return optional().orElseThrow(() -> new ResourceNotFoundException("Brak zalogowanego konta"));
    }
}
