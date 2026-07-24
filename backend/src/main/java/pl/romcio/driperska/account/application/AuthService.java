package pl.romcio.driperska.account.application;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.account.api.AccountDtos.AccountResponse;
import pl.romcio.driperska.account.api.AuthDtos.LoginRequest;
import pl.romcio.driperska.account.api.AuthDtos.TokenResponse;
import pl.romcio.driperska.account.domain.Account;
import pl.romcio.driperska.account.infra.AccountRepository;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.common.security.JwtService;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final JwtService jwtService;
    private final pl.romcio.driperska.integration.turnstile.TurnstileService turnstileService;

    public AuthService(AuthenticationManager authenticationManager,
                       AccountRepository accountRepository,
                       JwtService jwtService,
                       pl.romcio.driperska.integration.turnstile.TurnstileService turnstileService) {
        this.authenticationManager = authenticationManager;
        this.accountRepository = accountRepository;
        this.jwtService = jwtService;
        this.turnstileService = turnstileService;
    }

    @Transactional
    public TokenResponse login(LoginRequest req, String remoteIp) {
        if (!turnstileService.verify(req.turnstileToken(), remoteIp)) {
            throw new BadCredentialsException("Weryfikacja Cloudflare nie powiodła się — odśwież stronę i spróbuj ponownie");
        }
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        Account account = accountRepository.findByUsername(req.username())
                .orElseThrow(() -> new BadCredentialsException("Nieprawidłowy login lub hasło"));
        account.markLogin();
        return issueTokens(account);
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadCredentialsException("Nieprawidłowy token odświeżania");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new BadCredentialsException("Oczekiwano tokena odświeżania");
        }
        if (!jwtService.isCurrentBoot(claims)) {
            throw new BadCredentialsException("Sesja wygasła po przerwie technicznej — zaloguj się ponownie");
        }
        UUID accountId = UUID.fromString(claims.getSubject());
        Account account = accountRepository.findById(accountId)
                .filter(Account::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Konto niedostępne"));
        return issueTokens(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse me(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(AccountResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", accountId));
    }

    private TokenResponse issueTokens(Account account) {
        String access = jwtService.issueAccessToken(
                account.getId(), account.getUsername(), account.getRole().authority());
        String refresh = jwtService.issueRefreshToken(account.getId());
        return new TokenResponse(access, refresh, "Bearer",
                jwtService.accessTokenSeconds(), AccountResponse.from(account));
    }
}
