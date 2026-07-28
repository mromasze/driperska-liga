package pl.romcio.driperska.ranking.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.ranking.domain.ScoringConfig;
import pl.romcio.driperska.season.infra.SeasonRepository;

/** Supplies defaults merged with optional per-season JSON scoring overrides. */
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
            JsonNode parsed = objectMapper.readTree(json);
            if (!(parsed instanceof ObjectNode overrides)) {
                throw new IllegalArgumentException("Scoring config must be a JSON object");
            }
            ObjectNode merged = objectMapper.valueToTree(ScoringConfig.defaults());
            merged.setAll(overrides);
            return objectMapper.treeToValue(merged, ScoringConfig.class);
        } catch (Exception exception) {
            log.warn("Invalid scoring_config_json for season {}; using current defaults", seasonId, exception);
            return ScoringConfig.defaults();
        }
    }

    public ScoringConfig defaults() {
        return ScoringConfig.defaults();
    }
}
