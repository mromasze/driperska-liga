package pl.romcio.driperska.match.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.match.domain.MatchEvent;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.infra.MatchEventRepository;

/** Appends audit entries to a match's timeline. */
@Component
public class MatchEventRecorder {

    private final MatchEventRepository repository;
    private final ObjectMapper objectMapper;

    public MatchEventRecorder(MatchEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(UUID matchId, MatchEventType type, UUID actor, Map<String, ?> payload) {
        String json = null;
        if (payload != null && !payload.isEmpty()) {
            try {
                json = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException ignored) {
                json = null;
            }
        }
        repository.save(new MatchEvent(matchId, type, actor, json));
    }
}
