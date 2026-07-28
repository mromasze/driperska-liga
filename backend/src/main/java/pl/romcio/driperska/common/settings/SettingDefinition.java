package pl.romcio.driperska.common.settings;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One admin-editable runtime setting.
 *
 * <p>A definition knows how to <em>read</em> the value that is live right now ({@link #reader()},
 * normally a getter on the matching {@code @ConfigurationProperties} bean) and how to <em>apply</em> a
 * new one ({@link #writer()}, the matching setter). Persistence is separate: overrides live in the
 * {@code app_setting} table and are re-applied on boot, so a value edited in the panel survives a
 * restart without touching {@code .env}.
 *
 * <p>Settings that are consumed once at startup (JWT secret, media directory, Data Dragon) have no
 * writer — they are reported with {@code editable=false} so the panel can show what is running
 * without pretending it can be changed on the fly.
 *
 * @param key          storage key in {@code app_setting}, e.g. {@code ollama.vision-model}
 * @param envName      matching variable in {@code .env}, shown next to the field
 * @param group        section in the admin panel
 * @param restartNote  extra warning shown even for editable settings (e.g. "gateway po restarcie")
 * @param options      suggested values for {@link SettingType#CHOICE}; empty otherwise
 */
public record SettingDefinition(
        String key,
        String envName,
        String group,
        String label,
        String description,
        SettingType type,
        boolean editable,
        String restartNote,
        List<String> options,
        Supplier<String> reader,
        Consumer<String> writer) {

    public boolean isSecret() {
        return type == SettingType.SECRET;
    }

    /** Live value, normalised to {@code null} when blank so "not set" is unambiguous. */
    public String read() {
        String value = reader.get();
        return value == null || value.isBlank() ? null : value;
    }

    // --- factories -------------------------------------------------------------------------

    public static SettingDefinition text(String key, String env, String group, String label,
                                         String description,
                                         Supplier<String> reader, Consumer<String> writer) {
        return new SettingDefinition(key, env, group, label, description, SettingType.STRING,
                true, null, List.of(), reader, writer);
    }

    public static SettingDefinition secret(String key, String env, String group, String label,
                                           String description,
                                           Supplier<String> reader, Consumer<String> writer) {
        return new SettingDefinition(key, env, group, label, description, SettingType.SECRET,
                true, null, List.of(), reader, writer);
    }

    public static SettingDefinition flag(String key, String env, String group, String label,
                                         String description,
                                         Supplier<Boolean> reader, Consumer<Boolean> writer) {
        return new SettingDefinition(key, env, group, label, description, SettingType.BOOLEAN,
                true, null, List.of(),
                () -> Boolean.toString(Boolean.TRUE.equals(reader.get())),
                value -> writer.accept(Boolean.parseBoolean(value)));
    }

    public static SettingDefinition number(String key, String env, String group, String label,
                                           String description,
                                           Supplier<Integer> reader, Consumer<Integer> writer) {
        return new SettingDefinition(key, env, group, label, description, SettingType.INTEGER,
                true, null, List.of(),
                () -> String.valueOf(reader.get()),
                value -> writer.accept(Integer.parseInt(value.trim())));
    }

    public static SettingDefinition choice(String key, String env, String group, String label,
                                           String description, List<String> options,
                                           Supplier<String> reader, Consumer<String> writer) {
        return new SettingDefinition(key, env, group, label, description, SettingType.CHOICE,
                true, null, options, reader, writer);
    }

    /** Startup-only value: shown for reference, changed only in {@code .env} + restart. */
    public static SettingDefinition readOnly(String key, String env, String group, String label,
                                             String description, SettingType type,
                                             Supplier<String> reader) {
        return new SettingDefinition(key, env, group, label, description, type,
                false, "Wczytywane raz przy starcie — zmień w .env i zrestartuj backend.",
                List.of(), reader, null);
    }

    /** Same setting, with an extra caveat rendered under the field. */
    public SettingDefinition note(String restartNote) {
        return new SettingDefinition(key, envName, group, label, description, type, editable,
                restartNote, options, reader, writer);
    }

    public SettingDefinition withOptions(List<String> options) {
        return new SettingDefinition(key, envName, group, label, description, type, editable,
                restartNote, List.copyOf(options), reader, writer);
    }
}
