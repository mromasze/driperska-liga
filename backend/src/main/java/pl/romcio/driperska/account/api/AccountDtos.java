package pl.romcio.driperska.account.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import pl.romcio.driperska.account.domain.Account;
import pl.romcio.driperska.account.domain.AccountRole;

/** Request/response payloads for account management. */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountResponse(
            UUID id,
            String username,
            String email,
            AccountRole role,
            boolean enabled,
            Instant createdAt,
            Instant lastLoginAt) {

        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getUsername(), a.getEmail(),
                    a.getRole(), a.isEnabled(), a.getCreatedAt(), a.getLastLoginAt());
        }
    }

    public record CreateAccountRequest(
            @NotBlank @Size(min = 3, max = 40) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotNull AccountRole role) {
    }

    public record UpdateAccountRequest(
            AccountRole role,
            Boolean enabled,
            @Size(min = 8, max = 100) String newPassword) {
    }
}
