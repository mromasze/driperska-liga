package pl.romcio.driperska.account.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.account.api.AccountDtos.AccountResponse;
import pl.romcio.driperska.account.api.AuthDtos.LoginRequest;
import pl.romcio.driperska.account.api.AuthDtos.RefreshRequest;
import pl.romcio.driperska.account.api.AuthDtos.TokenResponse;
import pl.romcio.driperska.account.application.AuthService;
import pl.romcio.driperska.common.security.AuthenticatedAccount;
import pl.romcio.driperska.common.security.CurrentAccount;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public void logout() {
        // Stateless JWT: the client discards its tokens. A future revocation list can hook in here.
    }

    @GetMapping("/me")
    public AccountResponse me() {
        AuthenticatedAccount current = CurrentAccount.require();
        return authService.me(current.accountId());
    }
}
