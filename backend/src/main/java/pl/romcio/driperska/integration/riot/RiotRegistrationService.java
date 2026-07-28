package pl.romcio.driperska.integration.riot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pl.romcio.driperska.common.config.AppCoreProperties;
import pl.romcio.driperska.common.error.ExternalServiceException;

@Service
public class RiotRegistrationService {
    private final RiotProperties properties;
    private final RiotApiClient client;
    private final RiotTournamentRegistrationRepository repository;
    private final AppCoreProperties app;

    public RiotRegistrationService(RiotProperties properties, RiotApiClient client,
                                   RiotTournamentRegistrationRepository repository,
                                   AppCoreProperties app) {
        this.properties = properties;
        this.client = client;
        this.repository = repository;
        this.app = app;
    }

    @Transactional
    public RiotTournamentRegistration registration() {
        if (!properties.configured()) {
            throw new ExternalServiceException("Riot API", "brak RIOT_API_KEY w konfiguracji serwera");
        }
        String callbackUrl = StringUtils.hasText(properties.getCallbackUrl())
                ? properties.getCallbackUrl()
                : app.publicUrl() + "/api/v1/riot/tournament/callback";
        String fingerprint = fingerprint(properties.getApiKey());
        return repository.findByKeyFingerprintAndPlatformAndCallbackUrl(
                        fingerprint, properties.getPlatform(), callbackUrl)
                .orElseGet(() -> {
                    long providerId = client.createProvider(callbackUrl);
                    long tournamentId = client.createTournament(providerId);
                    return repository.save(new RiotTournamentRegistration(
                            fingerprint, properties.getPlatform(), callbackUrl,
                            providerId, tournamentId));
                });
    }

    private static String fingerprint(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}

