package pl.romcio.driperska.common.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Key/value store behind every admin-editable runtime setting.
 *
 * <p>Two kinds of value live here. The "Riot API support" flag is read straight from this table on
 * every use. Everything else (API keys, models, channel IDs — see {@link RuntimeConfigRegistry}) is
 * an <em>override</em> of a {@code .env} value: it is replayed onto the matching properties bean by
 * {@link RuntimeConfigService} at boot and whenever it is saved, so the rest of the app keeps
 * reading its own configuration and knows nothing about this table.
 */
@Service
public class AppSettingService {

    public static final String RIOT_ENABLED = "riot.enabled";

    private final AppSettingRepository repository;
    private final boolean riotEnabledDefault;

    public AppSettingService(AppSettingRepository repository,
                             @Value("${app.riot.enabled-default:false}") boolean riotEnabledDefault) {
        this.repository = repository;
        this.riotEnabledDefault = riotEnabledDefault;
    }

    @Transactional(readOnly = true)
    public boolean isRiotEnabled() {
        return getBool(RIOT_ENABLED, riotEnabledDefault);
    }

    @Transactional
    public void setRiotEnabled(boolean enabled) {
        put(RIOT_ENABLED, Boolean.toString(enabled));
    }

    /** Every stored override, keyed by setting key. Insertion order is irrelevant to callers. */
    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> values = new LinkedHashMap<>();
        repository.findAll().forEach(setting -> values.put(setting.getKey(), setting.getValue()));
        return values;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        return repository.findById(key).map(AppSetting::getValue);
    }

    /** Stores an override. A {@code null} value removes it, falling back to the {@code .env} default. */
    @Transactional
    public void set(String key, String value) {
        if (value == null) {
            repository.findById(key).ifPresent(repository::delete);
            return;
        }
        put(key, value);
    }

    private boolean getBool(String key, boolean fallback) {
        return repository.findById(key)
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(fallback);
    }

    private void put(String key, String value) {
        AppSetting setting = repository.findById(key).orElseGet(() -> new AppSetting(key, value));
        setting.setValue(value);
        repository.save(setting);
    }
}
