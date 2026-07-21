package pl.romcio.driperska.integration.riot;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.match.application.MatchEventRecorder;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.infra.MatchRepository;

@Service
public class RiotImportStateService {
    private final MatchRepository repository;
    private final MatchEventRecorder eventRecorder;

    public RiotImportStateService(MatchRepository repository, MatchEventRecorder eventRecorder) {
        this.repository = repository;
        this.eventRecorder = eventRecorder;
    }

    @Transactional
    public void success(UUID matchId, String riotMatchId) {
        Match match = repository.findForUpdate(matchId).orElseThrow();
        match.markRiotImportSuccess(riotMatchId);
        eventRecorder.record(matchId, MatchEventType.RIOT_RESULTS_IMPORTED, null,
                Map.of("riotMatchId", riotMatchId));
    }

    @Transactional
    public void failure(UUID matchId, String message) {
        repository.findById(matchId).ifPresent(match -> match.markRiotImportFailure(message));
        eventRecorder.record(matchId, MatchEventType.RIOT_IMPORT_FAILED, null,
                Map.of("error", message == null ? "unknown" : message));
    }
}

