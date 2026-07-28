package pl.romcio.driperska.match.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchRepository;

/**
 * Auto-confirms a drawn squad once it has been waiting for votes longer than the configured
 * timeout (default 30s). Each match is confirmed in its own transaction so one failure (e.g. a
 * transient Riot error) does not block the others. After a few consecutive failures for the same
 * match (e.g. no Tournament API access) it gives up so the logs are not flooded — the admin can
 * then start the match manually or enable RIOT_MOCK.
 */
@Component
public class DrawAutoConfirmScheduler {
    private static final Logger log = LoggerFactory.getLogger(DrawAutoConfirmScheduler.class);
    private static final int MAX_ATTEMPTS = 3;

    private final MatchRepository matchRepository;
    private final DrawLobbyService drawLobbyService;
    private final DrawProperties drawProperties;
    private final Map<UUID, Integer> failures = new ConcurrentHashMap<>();

    public DrawAutoConfirmScheduler(MatchRepository matchRepository, DrawLobbyService drawLobbyService,
                                    DrawProperties drawProperties) {
        this.matchRepository = matchRepository;
        this.drawLobbyService = drawLobbyService;
        this.drawProperties = drawProperties;
    }

    @Scheduled(fixedDelayString = "${app.draw.auto-confirm-poll-ms:5000}")
    public void confirmExpiredDraws() {
        long timeoutSeconds = drawProperties.getAutoConfirmSeconds();
        if (timeoutSeconds <= 0) {
            return; // feature disabled
        }
        Instant threshold = Instant.now().minus(Duration.ofSeconds(timeoutSeconds));
        List<UUID> due = matchRepository.findIdsForAutoConfirm(MatchStatus.TEAMS_DRAWN, threshold);
        for (UUID matchId : due) {
            if (failures.getOrDefault(matchId, 0) >= MAX_ATTEMPTS) {
                continue; // already gave up on this match
            }
            try {
                drawLobbyService.autoConfirm(matchId);
                failures.remove(matchId);
                log.info("Auto-confirmed draw for match {} after {}s timeout", matchId, timeoutSeconds);
            } catch (RuntimeException ex) {
                int attempts = failures.merge(matchId, 1, Integer::sum);
                if (attempts >= MAX_ATTEMPTS) {
                    log.warn("Auto-confirm gave up for match {} after {} attempts: {}. "
                            + "Start the match manually or set RIOT_MOCK=true (Tournament API access needed for real codes).",
                            matchId, attempts, ex.getMessage());
                } else {
                    log.warn("Auto-confirm attempt {}/{} failed for match {}: {}",
                            attempts, MAX_ATTEMPTS, matchId, ex.getMessage());
                }
            }
        }
    }
}
