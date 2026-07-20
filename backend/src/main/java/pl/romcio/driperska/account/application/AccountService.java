package pl.romcio.driperska.account.application;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.account.api.AccountDtos.CreateAccountRequest;
import pl.romcio.driperska.account.api.AccountDtos.UpdateAccountRequest;
import pl.romcio.driperska.account.domain.Account;
import pl.romcio.driperska.account.infra.AccountRepository;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;

@Service
public class AccountService {

    private final AccountRepository repository;
    private final PasswordEncoder passwordEncoder;

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
}
