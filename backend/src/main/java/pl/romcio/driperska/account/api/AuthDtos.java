package pl.romcio.driperska.account.api;

import jakarta.validation.constraints.NotBlank;
import pl.romcio.driperska.account.api.AccountDtos.AccountResponse;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            AccountResponse account) {
    }
}
