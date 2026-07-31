package pl.romcio.driperska.match.application.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.match.domain.MatchDraft;
import pl.romcio.driperska.match.infra.MatchDraftRepository;

/**
 * Reads and writes the draft document ({@code match_draft.state}).
 *
 * <p>Extracted because two services now share that one row: the draft itself and the pre-draft setup
 * (captain vote, pick order, readiness) that runs before the first ban. Both work on the same
 * {@link DraftState}, and having a single place that knows how it is serialised keeps them from
 * drifting apart.
 */
@Component
public class DraftStateStore {

    private final MatchDraftRepository repository;
    private final ObjectMapper objectMapper;

    public DraftStateStore(MatchDraftRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** The stored state, or a failure — used where a draft must already exist. */
    public DraftState require(UUID matchId) {
        return find(matchId).orElseThrow(() -> ResourceNotFoundException.of("Draft", matchId));
    }

    public Optional<DraftState> find(UUID matchId) {
        return repository.findById(matchId).map(draft -> deserialize(draft.getState()));
    }

    /**
     * The stored state, or a brand-new one. The setup phase starts before anything has been persisted,
     * so the first captain vote is what creates the row.
     */
    public DraftState findOrNew(UUID matchId) {
        return find(matchId).orElseGet(DraftState::new);
    }

    public void save(UUID matchId, DraftState state) {
        String json = serialize(state);
        MatchDraft draft = repository.findById(matchId).orElse(null);
        if (draft == null) {
            repository.save(new MatchDraft(matchId, json, state.deadline));
        } else {
            draft.update(json, state.deadline);
            repository.save(draft);
        }
    }

    private String serialize(DraftState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            throw new IllegalStateException("Nie udało się zapisać stanu draftu", ex);
        }
    }

    private DraftState deserialize(String json) {
        try {
            return objectMapper.readValue(json, DraftState.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Nie udało się odczytać stanu draftu", ex);
        }
    }
}
