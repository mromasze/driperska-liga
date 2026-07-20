package pl.romcio.driperska.ranking.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.ranking.domain.ScoringConfig;

/**
 * Supplies the scoring rules for a season. For now returns the defaults; a per-season JSON override
 * (stored on {@code Season.scoringConfigJson}) can be parsed here later without touching callers.
 */
@Component
public class ScoringConfigProvider {

    public ScoringConfig forSeason(UUID seasonId) {
        return ScoringConfig.defaults();
    }

    public ScoringConfig defaults() {
        return ScoringConfig.defaults();
    }
}
