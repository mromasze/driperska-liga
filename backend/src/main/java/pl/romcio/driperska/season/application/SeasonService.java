package pl.romcio.driperska.season.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.season.domain.Season;
import pl.romcio.driperska.season.domain.SeasonStatus;
import pl.romcio.driperska.season.infra.SeasonRepository;

@Service
public class SeasonService {

    private final SeasonRepository repository;

    public SeasonService(SeasonRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Season> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Season get(UUID id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Season", id));
    }

    @Transactional(readOnly = true)
    public Season current() {
        return repository.findFirstByStatus(SeasonStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Brak aktywnego sezonu"));
    }

    @Transactional
    public Season create(Season season) {
        return repository.save(season);
    }

    /** Activates the given season, demoting any currently-active season to archived. */
    @Transactional
    public Season activate(UUID id) {
        Season season = get(id);
        repository.findFirstByStatus(SeasonStatus.ACTIVE)
                .filter(active -> !active.getId().equals(id))
                .ifPresent(active -> active.setStatus(SeasonStatus.ARCHIVED));
        season.setStatus(SeasonStatus.ACTIVE);
        return season;
    }

    @Transactional
    public Season archive(UUID id) {
        Season season = get(id);
        if (season.getStatus() == SeasonStatus.UPCOMING) {
            throw new BusinessRuleException("Nie można zarchiwizować sezonu, który się nie rozpoczął");
        }
        season.setStatus(SeasonStatus.ARCHIVED);
        return season;
    }
}
