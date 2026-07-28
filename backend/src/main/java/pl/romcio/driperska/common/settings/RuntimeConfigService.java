package pl.romcio.driperska.common.settings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import pl.romcio.driperska.common.error.BusinessRuleException;

/**
 * Applies admin overrides on top of the {@code .env} configuration, at boot and on every save.
 *
 * <p>The {@code .env} values are captured in the constructor — before any override has been
 * replayed — so "przywróć z .env" always has something truthful to restore, and the panel can show
 * which fields have been changed away from the deployed defaults.
 */
@Service
public class RuntimeConfigService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeConfigService.class);
    private static final int MAX_VALUE_LENGTH = 2000;

    private final RuntimeConfigRegistry registry;
    private final AppSettingService settings;
    /** Value each setting had when the process started, i.e. straight from {@code .env}. */
    private final Map<String, String> envDefaults = new LinkedHashMap<>();

    public RuntimeConfigService(RuntimeConfigRegistry registry, AppSettingService settings) {
        this.registry = registry;
        this.settings = settings;
        registry.all().forEach(definition -> envDefaults.put(definition.key(), definition.read()));
    }

    /**
     * Replays stored overrides onto the properties beans once the context is up. Anything that was
     * already consumed during startup (see the read-only entries in the registry) is skipped.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void applyStoredOverrides() {
        int applied = 0;
        for (Map.Entry<String, String> entry : settings.all().entrySet()) {
            SettingDefinition definition = registry.find(entry.getKey());
            if (definition == null || !definition.editable()) {
                continue;
            }
            try {
                definition.writer().accept(entry.getValue());
                applied++;
            } catch (RuntimeException ex) {
                log.warn("Ignoring stored override for {} — {}", entry.getKey(), ex.getMessage());
            }
        }
        if (applied > 0) {
            log.info("Applied {} runtime configuration override(s) from the admin panel", applied);
        }
    }

    // --- reading ------------------------------------------------------------------------------

    public record SettingView(String key, String envName, String label, String description,
                              SettingType type, boolean editable, String restartNote,
                              List<String> options, boolean secret,
                              String value, boolean set, boolean overridden, String defaultValue) {}

    public record GroupView(String name, List<SettingView> settings) {}

    public List<GroupView> describe() {
        Map<String, String> overrides = settings.all();
        Map<String, List<SettingView>> grouped = new LinkedHashMap<>();
        for (SettingDefinition definition : registry.all()) {
            grouped.computeIfAbsent(definition.group(), key -> new ArrayList<>())
                    .add(view(definition, overrides.containsKey(definition.key())));
        }
        List<GroupView> groups = new ArrayList<>();
        grouped.forEach((name, entries) -> groups.add(new GroupView(name, entries)));
        groups.sort(Comparator.comparingInt(group -> {
            int index = RuntimeConfigRegistry.GROUP_ORDER.indexOf(group.name());
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        return groups;
    }

    private SettingView view(SettingDefinition definition, boolean overridden) {
        String live = definition.read();
        String envDefault = envDefaults.get(definition.key());
        return new SettingView(
                definition.key(), definition.envName(), definition.label(), definition.description(),
                definition.type(), definition.editable(), definition.restartNote(),
                definition.options(), definition.isSecret(),
                definition.isSecret() ? mask(live) : live,
                live != null,
                overridden,
                definition.isSecret() ? mask(envDefault) : envDefault);
    }

    /** Enough of a secret to recognise it, never enough to use it. */
    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "•".repeat(trimmed.length());
        }
        return trimmed.substring(0, 3) + "…" + trimmed.substring(trimmed.length() - 4);
    }

    // --- writing ------------------------------------------------------------------------------

    /**
     * Saves and applies a batch of changes.
     *
     * @param values key → new value; a {@code null} value drops the override and restores {@code .env}
     */
    public List<GroupView> update(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return describe();
        }
        // Validate everything first so a typo in one field cannot leave the batch half-applied.
        Map<String, String> normalised = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            SettingDefinition definition = registry.find(entry.getKey());
            if (definition == null) {
                throw new BusinessRuleException("Nieznane ustawienie: " + entry.getKey());
            }
            if (!definition.editable()) {
                throw new BusinessRuleException(
                        definition.label() + " można zmienić tylko w .env (wymaga restartu)");
            }
            normalised.put(entry.getKey(), validate(definition, entry.getValue()));
        }

        normalised.forEach((key, value) -> {
            SettingDefinition definition = registry.find(key);
            String effective = value != null ? value : envDefaults.get(key);
            definition.writer().accept(effective == null ? "" : effective);
            settings.set(key, value);
            log.info("Runtime setting {} {}", key, value == null ? "reset to .env default" : "updated");
        });
        return describe();
    }

    /** Drops overrides and restores the values the process booted with. */
    public List<GroupView> reset(List<String> keys) {
        Map<String, String> cleared = new LinkedHashMap<>();
        keys.forEach(key -> cleared.put(key, null));
        return update(cleared);
    }

    private String validate(SettingDefinition definition, String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new BusinessRuleException(definition.label() + ": wartość jest za długa (max "
                    + MAX_VALUE_LENGTH + " znaków)");
        }
        switch (definition.type()) {
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new BusinessRuleException(definition.label() + ": oczekiwano true/false");
                }
                return value.toLowerCase();
            }
            case INTEGER -> {
                try {
                    return String.valueOf(Integer.parseInt(value));
                } catch (NumberFormatException ex) {
                    throw new BusinessRuleException(definition.label() + ": oczekiwano liczby całkowitej");
                }
            }
            default -> {
                return value;
            }
        }
    }
}
