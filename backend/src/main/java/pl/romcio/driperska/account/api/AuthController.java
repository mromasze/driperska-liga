package pl.romcio.driperska.account.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.romcio.driperska.account.api.AccountDtos.AccountResponse;
import pl.romcio.driperska.account.api.AuthDtos.ChangePasswordRequest;
import pl.romcio.driperska.account.api.AuthDtos.LoginRequest;
import pl.romcio.driperska.account.api.AuthDtos.RefreshRequest;
import pl.romcio.driperska.account.api.AuthDtos.TokenResponse;
import pl.romcio.driperska.account.application.AccountService;
import pl.romcio.driperska.account.application.AuthService;
import pl.romcio.driperska.common.security.AuthenticatedAccount;
import pl.romcio.driperska.common.security.CurrentAccount;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AccountService accountService;

    public AuthController(AuthService authService, AccountService accountService) {
        this.authService = authService;
        this.accountService = accountService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.login(req, clientIp(http));
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

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        accountService.changePassword(CurrentAccount.require().accountId(),
                req.currentPassword(), req.newPassword());
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
