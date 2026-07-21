package pl.romcio.driperska.integration.riot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.match.application.MatchEventRecorder;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.infra.MatchRepository;

@Service
public class RiotCallbackService {
    private final MatchRepository matchRepository;
    private final MatchEventRecorder eventRecorder;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher publisher;
    private final RiotApiClient client;

    public RiotCallbackService(MatchRepository matchRepository, MatchEventRecorder eventRecorder,
                               ObjectMapper objectMapper, ApplicationEventPublisher publisher,
                               RiotApiClient client) {
        this.matchRepository = matchRepository;
        this.eventRecorder = eventRecorder;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.client = client;
    }

    @Transactional
    public void receive(RiotCallbackRequest request) {
        Match match = matchRepository.findByRiotTournamentCode(request.shortCode())
                .orElseThrow(() -> new BusinessRuleException("Nieznany lub nieaktualny kod turniejowy"));
        CallbackMetadata metadata = metadata(request.metaData());
        if (!match.getId().toString().equals(metadata.matchId())
                || !match.getRiotMetadataToken().equals(metadata.token())) {
            throw new BusinessRuleException("Nieprawidłowe metadane callbacku Riot");
        }
        String matchId = client.matchIdFor(request.gameId());
        match.markRiotCallback(request.gameId(), matchId);
        eventRecorder.record(match.getId(), MatchEventType.RIOT_CALLBACK_RECEIVED, null,
                Map.of("gameId", request.gameId(), "matchId", matchId));
        publisher.publishEvent(new RiotResultAvailable(match.getId()));
    }

    private CallbackMetadata metadata(String value) {
        try {
            JsonNode node = objectMapper.readTree(value);
            return new CallbackMetadata(node.path("matchId").asText(), node.path("token").asText());
        } catch (Exception ex) {
            throw new BusinessRuleException("Callback Riot zawiera nieprawidłowe metadata");
        }
    }

    public record RiotCallbackRequest(String startTime, String shortCode, String metaData,
                                      String gameId, String gameName, String gameType,
                                      Integer gameMap, String gameMode, String region) {}
    private record CallbackMetadata(String matchId, String token) {}
    public record RiotResultAvailable(java.util.UUID matchId) {}
}

