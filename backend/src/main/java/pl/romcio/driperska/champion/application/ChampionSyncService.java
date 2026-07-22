package pl.romcio.driperska.champion.application;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.champion.infra.DataDragonClient;
import pl.romcio.driperska.champion.infra.DataDragonClient.ChampionData;
import pl.romcio.driperska.champion.infra.DataDragonClient.ChampionListResponse;

@Service
public class ChampionSyncService {

    private static final Logger log = LoggerFactory.getLogger(ChampionSyncService.class);

    private final ChampionRepository repository;
    private final DataDragonClient client;

    public ChampionSyncService(ChampionRepository repository, DataDragonClient client) {
        this.repository = repository;
        this.client = client;
    }

    /**
     * Upserts all champions for the given version (or the latest if {@code null}).
     * Never deletes existing champions, so historical match data stays intact.
     *
     * @return number of champions synced
     */
    @Transactional
    public int sync(String requestedVersion) {
        String version = (requestedVersion == null || requestedVersion.isBlank())
                ? client.latestVersion()
                : requestedVersion;
        ChampionListResponse response = client.fetchChampions(version);
        if (response == null || response.data() == null) {
            throw new IllegalStateException("Data Dragon returned no champion data for " + version);
        }

        List<Champion> toSave = new ArrayList<>();
        for (ChampionData data : response.data().values()) {
            int key = Integer.parseInt(data.key());
            Champion champion = repository.findById(key).orElseGet(() -> new Champion(key, data.id(), data.name()));
            champion.setSlug(data.id());
            champion.setName(data.name());
            champion.setTitle(data.title());
            champion.setTags(data.tags() == null ? "" : String.join(",", data.tags()));
            champion.setDdragonVersion(version);
            champion.setIconUrl(client.iconUrl(version, data.id()));
            champion.setSplashUrl(client.splashUrl(data.id()));
            champion.setLoadingUrl(client.loadingUrl(data.id()));
            toSave.add(champion);
        }
        repository.saveAll(toSave);
        log.info("Synced {} champions from Data Dragon {}", toSave.size(), version);
        return toSave.size();
    }

    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return repository.count() == 0;
    }
}
