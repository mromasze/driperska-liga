package pl.romcio.driperska.ranking.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.ranking.domain.ScoringConfig;
import pl.romcio.driperska.season.infra.SeasonRepository;

/** Supplies defaults or a complete per-season JSON scoring configuration. */
@Component
public class ScoringConfigProvider {

    private static final Logger log = LoggerFactory.getLogger(ScoringConfigProvider.class);

    private final SeasonRepository seasonRepository;
    private final ObjectMapper objectMapper;

    public ScoringConfigProvider(SeasonRepository seasonRepository, ObjectMapper objectMapper) {
        this.seasonRepository = seasonRepository;
        this.objectMapper = objectMapper;
    }

    public ScoringConfig forSeason(UUID seasonId) {
        return seasonRepository.findById(seasonId)
                .map(season -> season.getScoringConfigJson())
                .filter(json -> json != null && !json.isBlank())
                .map(json -> parse(seasonId, json))
                .orElseGet(ScoringConfig::defaults);
    }

    private ScoringConfig parse(UUID seasonId, String json) {
        try {
            return objectMapper.readValue(json, ScoringConfig.class);
        } catch (Exception exception) {
            log.warn("Invalid scoring_config_json for season {}; using v2 defaults", seasonId, exception);
            return ScoringConfig.defaults();
        }
    }

    public ScoringConfig defaults() {
        return ScoringConfig.defaults();
    }
}
