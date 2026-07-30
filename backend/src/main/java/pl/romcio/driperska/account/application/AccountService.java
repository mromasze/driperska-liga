package pl.romcio.driperska.account.application;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
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

    /**
     * Grants or revokes the moderator permission. Checked against the database on every moderation
     * request (not carried in the access token), so a revocation takes effect on the next click
     * rather than in twelve hours when the token expires.
     */
    @Transactional
    public Account setModerator(UUID accountId, boolean moderator) {
        Account account = get(accountId);
        account.setModerator(moderator);
        return account;
    }

    @Transactional(readOnly = true)
    public boolean isModerator(UUID accountId) {
        return repository.findById(accountId).filter(Account::isEnabled)
                .map(Account::isModerator).orElse(false);
    }

    /** The subset of the given accounts that carry the moderator flag (one query, for listings). */
    @Transactional(readOnly = true)
    public Set<UUID> moderatorsAmong(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) return Set.of();
        return repository.findAllById(accountIds).stream()
                .filter(Account::isModerator)
                .map(Account::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Optional<Account> find(UUID accountId) {
        return repository.findById(accountId);
    }

    @Transactional
    public Account update(UUID id, UpdateAccountRequest req) {
        Account account = get(id);
        if (req.role() != null) {
            account.setRole(req.role());
        }
        if (req.moderator() != null) {
            account.setModerator(req.moderator());
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
    public void changePassword(UUID accountId, String currentPassword, String newPassword) {
        Account account = get(accountId);
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new pl.romcio.driperska.common.error.BusinessRuleException("Aktualne hasło jest nieprawidłowe");
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword));
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