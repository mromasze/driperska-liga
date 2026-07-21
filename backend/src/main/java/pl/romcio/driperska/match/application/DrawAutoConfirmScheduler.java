package pl.romcio.driperska.match.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchRepository;

/**
 * Auto-confirms a drawn squad once it has been waiting for votes longer than the configured
 * timeout (default 30s). Each match is confirmed in its own transaction so one failure (e.g. a
 * transient Riot error) does not block the others.
 */
@Component
public class DrawAutoConfirmScheduler {
    private static final Logger log = LoggerFactory.getLogger(DrawAutoConfirmScheduler.class);

    private final MatchRepository matchRepository;
    private final DrawLobbyService drawLobbyService;
    private final long timeoutSeconds;

    public DrawAutoConfirmScheduler(MatchRepository matchRepository, DrawLobbyService drawLobbyService,
                                    @Value("${app.draw.auto-confirm-seconds:30}") long timeoutSeconds) {
        this.matchRepository = matchRepository;
        this.drawLobbyService = drawLobbyService;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${app.draw.auto-confirm-poll-ms:5000}")
    public void confirmExpiredDraws() {
        if (timeoutSeconds <= 0) {
            return; // feature disabled
        }
        Instant threshold = Instant.now().minus(Duration.ofSeconds(timeoutSeconds));
        List<UUID> due = matchRepository.findIdsForAutoConfirm(MatchStatus.TEAMS_DRAWN, threshold);
        for (UUID matchId : due) {
            try {
                drawLobbyService.autoConfirm(matchId);
                log.info("Auto-confirmed draw for match {} after {}s timeout", matchId, timeoutSeconds);
            } catch (RuntimeException ex) {
                log.warn("Auto-confirm failed for match {}: {}", matchId, ex.getMessage());
            }
        }
    }
}
