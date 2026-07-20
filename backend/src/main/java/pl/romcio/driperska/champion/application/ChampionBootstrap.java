package pl.romcio.driperska.champion.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.champion.infra.DataDragonProperties;

/** Bootstraps champion data on startup (if empty) and refreshes it daily. */
@Component
public class ChampionBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ChampionBootstrap.class);

    private final ChampionSyncService syncService;
    private final DataDragonProperties properties;

    public ChampionBootstrap(ChampionSyncService syncService, DataDragonProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!properties.syncOnStartup()) {
            return;
        }
        if (syncService.isEmpty()) {
            trySync();
        }
    }

    /** Daily at 04:00 — pick up new patches. */
    @Scheduled(cron = "0 0 4 * * *")
    public void daily() {
        trySync();
    }

    private void trySync() {
        try {
            syncService.sync(null);
        } catch (Exception ex) {
            // Never let a CDN hiccup crash the app; keep whatever is already in the DB.
            log.warn("Champion sync failed, keeping existing data: {}", ex.getMessage());
        }
    }
}
