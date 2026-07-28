package pl.romcio.driperska.common.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import pl.romcio.driperska.champion.infra.DataDragonProperties;
import pl.romcio.driperska.common.config.AppCoreProperties;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.security.JwtProperties;
import pl.romcio.driperska.integration.discord.DiscordProperties;
import pl.romcio.driperska.integration.ollama.OllamaProperties;
import pl.romcio.driperska.integration.riot.RiotProperties;
import pl.romcio.driperska.integration.turnstile.TurnstileProperties;
import pl.romcio.driperska.match.application.DraftProperties;
import pl.romcio.driperska.match.application.DrawProperties;
import pl.romcio.driperska.player.infra.StorageProperties;

class RuntimeConfigServiceTest {

    private OllamaProperties ollama;
    private DraftProperties draft;
    private Map<String, String> stored;
    private AppSettingService settings;
    private RuntimeConfigRegistry registry;

    @BeforeEach
    void setUp() {
        ollama = new OllamaProperties();
        ollama.setApiKey("env-key-abcdefgh");
        ollama.setVisionModel("qwen3.5:397b");
        draft = new DraftProperties();

        registry = new RuntimeConfigRegistry(ollama, new RiotProperties(), new DiscordProperties(),
                new TurnstileProperties(), draft, new DrawProperties(), new AppCoreProperties(),
                new JwtProperties("a-secret", 60, 7), new StorageProperties("./media", "/media"),
                new DataDragonProperties("https://ddragon.leagueoflegends.com", "en_US", false),
                new MockEnvironment());

        stored = new LinkedHashMap<>();
        settings = mock(AppSettingService.class);
        when(settings.all()).thenAnswer(call -> new LinkedHashMap<>(stored));
        doAnswer(call -> {
            String key = call.getArgument(0);
            String value = call.getArgument(1);
            if (value == null) stored.remove(key); else stored.put(key, value);
            return null;
        }).when(settings).set(anyString(), any());
    }

    private RuntimeConfigService service() {
        return new RuntimeConfigService(registry, settings);
    }

    private RuntimeConfigService.SettingView find(List<RuntimeConfigService.GroupView> groups, String key) {
        return groups.stream()
                .flatMap(group -> group.settings().stream())
                .filter(setting -> setting.key().equals(key))
                .findFirst().orElseThrow();
    }

    @Test
    void savingAModelAppliesItToTheLiveBeanAndRemembersTheEnvDefault() {
        RuntimeConfigService service = service();

        List<RuntimeConfigService.GroupView> groups =
                service.update(Map.of("ollama.vision-model", "llama4-scout:17b"));

        assertThat(ollama.getVisionModel()).isEqualTo("llama4-scout:17b");
        assertThat(stored).containsEntry("ollama.vision-model", "llama4-scout:17b");
        RuntimeConfigService.SettingView view = find(groups, "ollama.vision-model");
        assertThat(view.value()).isEqualTo("llama4-scout:17b");
        assertThat(view.overridden()).isTrue();
        assertThat(view.defaultValue()).isEqualTo("qwen3.5:397b");
    }

    @Test
    void resetRestoresTheValueTheProcessBootedWith() {
        RuntimeConfigService service = service();
        service.update(Map.of("ollama.vision-model", "llama4-scout:17b"));

        List<RuntimeConfigService.GroupView> groups = service.reset(List.of("ollama.vision-model"));

        assertThat(ollama.getVisionModel()).isEqualTo("qwen3.5:397b");
        assertThat(stored).doesNotContainKey("ollama.vision-model");
        assertThat(find(groups, "ollama.vision-model").overridden()).isFalse();
    }

    @Test
    void secretsAreOnlyEverReportedMasked() {
        RuntimeConfigService service = service();

        RuntimeConfigService.SettingView view = find(service.describe(), "ollama.api-key");

        assertThat(view.value()).isEqualTo("env…efgh").isNotEqualTo(ollama.getApiKey());
        assertThat(view.set()).isTrue();
    }

    @Test
    void integersAndBooleansAreValidatedBeforeAnythingIsApplied() {
        RuntimeConfigService service = service();

        assertThatThrownBy(() -> service.update(new LinkedHashMap<>(Map.of(
                "ollama.vision-model", "llama4-scout:17b",
                "ollama.timeout-seconds", "not-a-number"))))
                .isInstanceOf(BusinessRuleException.class);

        // The valid half of the batch must not have leaked through.
        assertThat(ollama.getVisionModel()).isEqualTo("qwen3.5:397b");
        assertThat(stored).isEmpty();
    }

    @Test
    void startupOnlySettingsAreRejected() {
        assertThatThrownBy(() -> service().update(Map.of("jwt.secret", "hijack")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(".env");
    }

    @Test
    void storedOverridesAreReplayedOntoTheBeansOnBoot() {
        stored.put("ollama.vision-model", "gemma4:31b");
        stored.put("draft.step-seconds", "45");
        stored.put("jwt.secret", "ignored-because-startup-only");

        service().applyStoredOverrides();

        assertThat(ollama.getVisionModel()).isEqualTo("gemma4:31b");
        assertThat(draft.getStepSeconds()).isEqualTo(45);
    }
}
