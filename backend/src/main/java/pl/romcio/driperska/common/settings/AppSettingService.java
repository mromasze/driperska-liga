package pl.romcio.driperska.common.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-editable runtime settings. Currently only the "Riot API support" flag, which decides
 * whether accepting a squad creates a Riot tournament lobby (and shows a join code) or starts
 * the internal champion draft instead. Defaults come from properties until an admin overrides them.
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
