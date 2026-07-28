package pl.romcio.driperska.ranking.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.common.settings.AppSetting;
import pl.romcio.driperska.common.settings.AppSettingRepository;
import pl.romcio.driperska.season.infra.SeasonRepository;

/**
 * Rebuilds stored PR/LP/ACE aggregates once after deploying ranking v2. The marker makes the
 * migration idempotent; subsequent starts do not touch historical standings.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RankingV2MigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RankingV2MigrationRunner.class);
    private static final String KEY = "ranking.schema.version";
    private static final String VERSION = "2";

    private final AppSettingRepository settings;
    private final SeasonRepository seasons;
    private final RankingService rankingService;

    public RankingV2MigrationRunner(AppSettingRepository settings, SeasonRepository seasons,
                                    RankingService rankingService) {
        this.settings = settings;
        this.seasons = seasons;
        this.rankingService = rankingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.findById(KEY).map(AppSetting::getValue).filter(VERSION::equals).isPresent()) {
            return;
        }
        log.info("Recalculating all seasons with ranking v2");
        seasons.findAll().forEach(season -> rankingService.recalculateSeason(season.getId()));
        AppSetting marker = settings.findById(KEY).orElseGet(() -> new AppSetting(KEY, VERSION));
        marker.setValue(VERSION);
        settings.save(marker);
        log.info("Ranking v2 recalculation completed");
    }
}
