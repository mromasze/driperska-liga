package pl.romcio.driperska.account.application;

import java.security.SecureRandom;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.account.api.AccountDtos.CreateAccountRequest;
import pl.romcio.driperska.account.api.AccountDtos.UpdateAccountRequest;
import pl.romcio.driperska.account.domain.Account;
import pl.romcio.driperska.account.domain.AccountRole;
import pl.romcio.driperska.account.infra.AccountRepository;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;

@Service
public class AccountService {

    private static final char[] PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
    private static final int GENERATED_PASSWORD_LENGTH = 16;

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public record ProvisionedAccount(Account account, String temporaryPassword) {
    }

    public AccountService(AccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<Account> list(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Account get(UUID id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Account", id));
    }

    @Transactional
    public Account create(CreateAccountRequest req) {
        if (repository.existsByUsername(req.username())) {
            throw new BusinessRuleException("Nazwa użytkownika jest już zajęta");
        }
        if (repository.existsByEmail(req.email())) {
            throw new BusinessRuleException("Adres e-mail jest już zajęty");
        }
        Account account = new Account(
                req.username(),
                req.email(),
                passwordEncoder.encode(req.password()),
                req.role());
        return repository.save(account);
    }

    /** Creates a PLAYER account and returns its one-time visible password. */
    @Transactional
    public ProvisionedAccount provisionPlayer(String nickname) {
        if (repository.existsByUsername(nickname)) {
            throw new BusinessRuleException("Konto dla tego nicku już istnieje");
        }
        String password = randomPassword();
        String internalEmail = UUID.randomUUID() + "@players.driperska.pl";
        Account account = repository.save(new Account(
                nickname,
                internalEmail,
                passwordEncoder.encode(password),
                AccountRole.PLAYER));
        return new ProvisionedAccount(account, password);
    }

    @Transactional
    public ProvisionedAccount resetTemporaryPassword(UUID accountId) {
        Account account = get(accountId);
        String password = randomPassword();
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setEnabled(true);
        return new ProvisionedAccount(account, password);
    }

    @Transactional
    public Account update(UUID id, UpdateAccountRequest req) {
        Account account = get(id);
        if (req.role() != null) {
            account.setRole(req.role());
        }
        if (req.enabled() != null) {
            account.setEnabled(req.enabled());
        }
        if (req.newPassword() != null && !req.newPassword().isBlank()) {
            account.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        }
        return account;
    }

    @Transactional
    public void deactivate(UUID id) {
        Account account = get(id);
        account.setEnabled(false);
    }

    private String randomPassword() {
        StringBuilder value = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            value.append(PASSWORD_CHARS[secureRandom.nextInt(PASSWORD_CHARS.length)]);
        }
        return value.toString();
    }
}