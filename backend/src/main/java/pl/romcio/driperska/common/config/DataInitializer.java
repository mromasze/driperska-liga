package pl.romcio.driperska.common.config;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.account.domain.Account;
import pl.romcio.driperska.account.domain.AccountRole;
import pl.romcio.driperska.account.infra.AccountRepository;
import pl.romcio.driperska.season.domain.Season;
import pl.romcio.driperska.season.domain.SeasonStatus;
import pl.romcio.driperska.season.infra.SeasonRepository;

/** Seeds the first admin account and an active season on an empty database. */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AccountRepository accountRepository;
    private final SeasonRepository seasonRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(AccountRepository accountRepository,
                           SeasonRepository seasonRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.bootstrap.admin-username:admin}") String adminUsername,
                           @Value("${app.bootstrap.admin-email:admin@driperska.local}") String adminEmail,
                           @Value("${app.bootstrap.admin-password:changeit123}") String adminPassword) {
        this.accountRepository = accountRepository;
        this.seasonRepository = seasonRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (accountRepository.count() == 0) {
            accountRepository.save(new Account(adminUsername, adminEmail,
                    passwordEncoder.encode(adminPassword), AccountRole.ADMIN));
            log.info("Seeded first admin account '{}'. CHANGE THE PASSWORD after first login.", adminUsername);
        }
        if (seasonRepository.findFirstByStatus(SeasonStatus.ACTIVE).isEmpty() && seasonRepository.count() == 0) {
            Season season = new Season("Sezon 1", LocalDate.now(), null);
            season.setStatus(SeasonStatus.ACTIVE);
            seasonRepository.save(season);
            log.info("Seeded active season '{}'", season.getName());
        }
    }
}
