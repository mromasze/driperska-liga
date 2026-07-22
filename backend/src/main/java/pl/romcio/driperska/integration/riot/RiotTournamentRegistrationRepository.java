package pl.romcio.driperska.integration.riot;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiotTournamentRegistrationRepository
        extends JpaRepository<RiotTournamentRegistration, UUID> {
    Optional<RiotTournamentRegistration> findByKeyFingerprintAndPlatformAndCallbackUrl(
            String keyFingerprint, String platform, String callbackUrl);
}

