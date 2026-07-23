package pl.romcio.driperska.match.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.match.infra.MatchDraftRepository;

/**
 * Enforces the per-step draft timer: any draft whose current ban/pick has run past its deadline is
 * auto-resolved with a random available champion, then the new state is broadcast.
 */
@Component
public class DraftScheduler {
    private static final Logger log = LoggerFactory.getLogger(DraftScheduler.class);

    private final MatchDraftRepository draftRepository;
    private final DraftService draftService;
    private final DrawLobbyService lobbyService;

    public DraftScheduler(MatchDraftRepository draftRepository, DraftService draftService,
                          DrawLobbyService lobbyService) {
        this.draftRepository = draftRepository;
        this.draftService = draftService;
        this.lobbyService = lobbyService;
    }

    @Scheduled(fixedRateString = "${app.draft.poll-ms:2000}")
    public void tick() {
        List<UUID> due = draftRepository.findMatchIdsPastDeadline(Instant.now());
        for (UUID matchId : due) {
            try {
                draftService.resolveExpired(matchId);
                lobbyService.publishUpdate(matchId);
            } catch (RuntimeException ex) {
                log.warn("Auto-resolving draft step for match {} failed: {}", matchId, ex.getMessage());
            }
        }
    }
}
