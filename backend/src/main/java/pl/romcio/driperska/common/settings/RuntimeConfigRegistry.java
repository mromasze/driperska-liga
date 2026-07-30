package pl.romcio.driperska.common.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.champion.infra.DataDragonProperties;
import pl.romcio.driperska.common.config.AppCoreProperties;
import pl.romcio.driperska.common.security.JwtProperties;
import pl.romcio.driperska.integration.discord.DiscordProperties;
import pl.romcio.driperska.integration.ollama.OllamaProperties;
import pl.romcio.driperska.integration.riot.RiotProperties;
import pl.romcio.driperska.integration.turnstile.TurnstileProperties;
import pl.romcio.driperska.match.application.DraftProperties;
import pl.romcio.driperska.match.application.DrawProperties;
import pl.romcio.driperska.player.infra.StorageProperties;

/**
 * The catalogue of everything the admin panel can show (and mostly change) from {@code .env}.
 *
 * <p>Each entry is wired straight to the getter/setter of the {@code @ConfigurationProperties} bean
 * the rest of the app already reads, so a saved change takes effect on the next call — no restart,
 * no second source of truth. Values consumed once during startup are registered read-only.
 */
@Component
public class RuntimeConfigRegistry {

    public static final String GROUP_AI = "AI (Ollama)";
    public static final String GROUP_RIOT = "Riot API";
    public static final String GROUP_DISCORD = "Discord";
    public static final String GROUP_TURNSTILE = "Cloudflare Turnstile";
    public static final String GROUP_GAMEPLAY = "Draft i losowanie";
    public static final String GROUP_APP = "Aplikacja";
    public static final String GROUP_STARTUP = "Tylko .env (wymaga restartu)";

    /** Order the panel renders groups in. */
    public static final List<String> GROUP_ORDER = List.of(
            GROUP_AI, GROUP_RIOT, GROUP_DISCORD, GROUP_TURNSTILE, GROUP_GAMEPLAY, GROUP_APP, GROUP_STARTUP);

    private final Map<String, SettingDefinition> byKey = new LinkedHashMap<>();

    public RuntimeConfigRegistry(OllamaProperties ollama, RiotProperties riot, DiscordProperties discord,
                                 TurnstileProperties turnstile, DraftProperties draft, DrawProperties draw,
                                 AppCoreProperties app, JwtProperties jwt, StorageProperties storage,
                                 DataDragonProperties ddragon, Environment environment) {
        List<SettingDefinition> all = new ArrayList<>();

        // --- AI ------------------------------------------------------------------------------
        all.add(SettingDefinition.text("ollama.base-url", "OLLAMA_BASE_URL", GROUP_AI,
                "Adres API", "Host Ollama. Chmura: https://ollama.com, lokalnie zwykle http://localhost:11434.",
                ollama::getBaseUrl, ollama::setBaseUrl));
        all.add(SettingDefinition.secret("ollama.api-key", "OLLAMA_API_KEY", GROUP_AI,
                "Klucz API", "Klucz z https://ollama.com/settings/keys. Puste = odczyt screenshotów wyłączony.",
                ollama::getApiKey, ollama::setApiKey));
        all.add(SettingDefinition.choice("ollama.vision-model", "OLLAMA_VISION_MODEL", GROUP_AI,
                "Model wizyjny", "Model czytający screenshoty z końca gry. Musi obsługiwać obrazy.",
                List.of(), ollama::getVisionModel, ollama::setVisionModel));
        all.add(SettingDefinition.number("ollama.timeout-seconds", "OLLAMA_TIMEOUT_SECONDS", GROUP_AI,
                "Limit czasu (s)", "Ile czekamy na odpowiedź modelu. Wolniejsze modele potrzebują więcej.",
                ollama::getTimeoutSeconds, ollama::setTimeoutSeconds));
        all.add(SettingDefinition.flag("ollama.atlas-enabled", "OLLAMA_ATLAS_ENABLED", GROUP_AI,
                "Atlas postaci",
                "Dołącza podpisane portrety postaci do zapytania — główna pomoc w rozpoznawaniu championów. "
                        + "Wyłącz, jeśli wolny model przekracza limit czasu.",
                ollama::isAtlasEnabled, ollama::setAtlasEnabled));

        // --- Riot ----------------------------------------------------------------------------
        all.add(SettingDefinition.secret("riot.api-key", "RIOT_API_KEY", GROUP_RIOT,
                "Klucz API", "Produkcyjny klucz Riot. Przełącznik „Wsparcie Riot API” jest wyżej, w Ustawieniach.",
                riot::getApiKey, riot::setApiKey));
        all.add(SettingDefinition.text("riot.platform", "RIOT_PLATFORM", GROUP_RIOT,
                "Platforma", "Host platformy, np. eun1.", riot::getPlatform, riot::setPlatform));
        all.add(SettingDefinition.text("riot.provider-region", "RIOT_PROVIDER_REGION", GROUP_RIOT,
                "Region providera", "Region rejestracji providera turniejowego, np. EUNE.",
                riot::getProviderRegion, riot::setProviderRegion));
        all.add(SettingDefinition.text("riot.regional-route", "RIOT_REGIONAL_ROUTE", GROUP_RIOT,
                "Trasa regionalna", "Klaster dla match-v5 / account-v1, np. europe.",
                riot::getRegionalRoute, riot::setRegionalRoute));
        all.add(SettingDefinition.text("riot.tournament-route", "RIOT_TOURNAMENT_ROUTE", GROUP_RIOT,
                "Trasa turniejowa", "Endpointy turniejowe żyją na klastrze regionalnym (americas), nie na platformie.",
                riot::getTournamentRoute, riot::setTournamentRoute));
        all.add(SettingDefinition.flag("riot.use-stub", "RIOT_USE_STUB", GROUP_RIOT,
                "Tryb stub", "Włączone = tournament-stub-v5 (działa z kluczem dev, kody NIE działają w kliencie). "
                        + "Wyłączone = prawdziwe tournament-v5 (wymaga zatwierdzonego klucza produkcyjnego).",
                riot::isUseStub, riot::setUseStub));
        all.add(SettingDefinition.flag("riot.mock", "RIOT_MOCK", GROUP_RIOT,
                "Symulacja Riot", "Wszystkie wywołania Riot są udawane — cały przepływ można przetestować bez kont.",
                riot::isMock, riot::setMock));
        all.add(SettingDefinition.text("riot.callback-url", "RIOT_CALLBACK_URL", GROUP_RIOT,
                "URL callbacku", "Puste = wyliczane z adresu publicznego aplikacji.",
                riot::getCallbackUrl, riot::setCallbackUrl));
        all.add(SettingDefinition.text("riot.tournament-name", "RIOT_TOURNAMENT_NAME", GROUP_RIOT,
                "Nazwa turnieju", "Widoczna po stronie Riot przy rejestracji turnieju.",
                riot::getTournamentName, riot::setTournamentName));

        // --- Discord -------------------------------------------------------------------------
        String gatewayNote = "Wysyłka wiadomości działa od razu. Nasłuch głosów RSVP (websocket) "
                + "łączy się przy starcie — zrestartuj backend, aby użyć nowego tokenu.";
        all.add(SettingDefinition.secret("discord.bot-token", "DISCORD_BOT_TOKEN", GROUP_DISCORD,
                "Token bota", "Bot musi być na serwerze i mieć prawo pisać na kanałach poniżej.",
                discord::getBotToken, discord::setBotToken).note(gatewayNote));
        all.add(SettingDefinition.text("discord.guild-id", "DISCORD_GUILD_ID", GROUP_DISCORD,
                "ID serwera", "ID gildii, na której działa bot.",
                discord::getGuildId, discord::setGuildId).note(gatewayNote));
        all.add(SettingDefinition.text("discord.results-channel-id", "DISCORD_RESULTS_CHANNEL_ID", GROUP_DISCORD,
                "Kanał wyników", "Tam trafiają obrazki z wynikami meczów.",
                discord::getResultsChannelId, discord::setResultsChannelId));
        all.add(SettingDefinition.text("discord.announce-channel-id", "DISCORD_ANNOUNCE_CHANNEL_ID", GROUP_DISCORD,
                "Kanał ogłoszeń", "Zapowiedzi meczów (@everyone). Puste = kanał wyników.",
                discord::getAnnounceChannelId, discord::setAnnounceChannelId));
        all.add(SettingDefinition.text("discord.vote-channel-id", "DISCORD_VOTE_CHANNEL_ID", GROUP_DISCORD,
                "Kanał głosowania", "Przyciski RSVP przy zaplanowanych meczach. Puste = kanał ogłoszeń.",
                discord::getVoteChannelId, discord::setVoteChannelId));
        all.add(SettingDefinition.text("discord.moderation-channel-id", "DISCORD_MODERATION_CHANNEL_ID",
                GROUP_DISCORD, "Kanał moderacji",
                "Zgłoszenia meczów wpisanych przez moderatorów (bez @everyone). Puste = kanał ogłoszeń.",
                discord::getModerationChannelId, discord::setModerationChannelId));
        all.add(SettingDefinition.text("discord.patch-notes-channel-id", "DISCORD_PATCH_CHANNEL_ID", GROUP_DISCORD,
                "Kanał patch notes", "Dedykowany kanał na zmiany w aplikacji. Bez niego wysyłka patch notes jest wyłączona.",
                discord::getPatchNotesChannelId, discord::setPatchNotesChannelId));

        // --- Turnstile -----------------------------------------------------------------------
        all.add(SettingDefinition.text("turnstile.site-key", "TURNSTILE_SITE_KEY", GROUP_TURNSTILE,
                "Site key", "Captcha przy logowaniu włącza się dopiero, gdy oba pola są ustawione.",
                turnstile::getSiteKey, turnstile::setSiteKey));
        all.add(SettingDefinition.secret("turnstile.secret", "TURNSTILE_SECRET", GROUP_TURNSTILE,
                "Secret", "Klucz prywatny do weryfikacji tokenu po stronie serwera.",
                turnstile::getSecret, turnstile::setSecret));

        // --- Gameplay ------------------------------------------------------------------------
        all.add(SettingDefinition.number("draft.step-seconds", "DRAFT_STEP_SECONDS", GROUP_GAMEPLAY,
                "Czas kroku draftu (s)", "Ile sekund ma gracz na ban/pick, zanim postać zostanie wybrana automatycznie.",
                draft::getStepSeconds, draft::setStepSeconds));
        all.add(SettingDefinition.number("draw.auto-confirm-seconds", "DRAW_AUTO_CONFIRM_SECONDS", GROUP_GAMEPLAY,
                "Auto-akceptacja składu (s)", "Po tylu sekundach wylosowany skład zatwierdza się sam. 0 = wyłączone.",
                draw::getAutoConfirmSeconds, draw::setAutoConfirmSeconds));

        // --- App -----------------------------------------------------------------------------
        all.add(SettingDefinition.text("app.public-url", "APP_PUBLIC_URL", GROUP_APP,
                "Adres publiczny", "Używany w linkach wysyłanych na Discorda i w danych logowania graczy.",
                app::getPublicUrl, app::setPublicUrl));

        // --- Startup-only --------------------------------------------------------------------
        all.add(SettingDefinition.readOnly("jwt.secret", "JWT_SECRET", GROUP_STARTUP,
                "Sekret JWT", "Podmiana unieważniłaby wszystkie sesje — dlatego tylko przy starcie.",
                SettingType.SECRET, jwt::secret));
        all.add(SettingDefinition.readOnly("jwt.access-token-minutes", "JWT_ACCESS_TOKEN_MINUTES", GROUP_STARTUP,
                "Ważność tokenu (min)", "Czas życia tokenu dostępowego.",
                SettingType.INTEGER, () -> String.valueOf(jwt.accessTokenMinutes())));
        all.add(SettingDefinition.readOnly("jwt.refresh-token-days", "JWT_REFRESH_TOKEN_DAYS", GROUP_STARTUP,
                "Ważność odświeżania (dni)", "Czas życia tokenu odświeżającego.",
                SettingType.INTEGER, () -> String.valueOf(jwt.refreshTokenDays())));
        all.add(SettingDefinition.readOnly("storage.media-dir", "MEDIA_DIR", GROUP_STARTUP,
                "Katalog mediów", "Awatary, powtórki i zagrywki. Mapowany na dysk przy starcie.",
                SettingType.STRING, storage::mediaDir));
        all.add(SettingDefinition.readOnly("ddragon.base-url", "—", GROUP_STARTUP,
                "Data Dragon — adres", "Klient HTTP do Data Dragona powstaje raz, przy starcie.",
                SettingType.STRING, ddragon::baseUrl));
        all.add(SettingDefinition.readOnly("ddragon.locale", "—", GROUP_STARTUP,
                "Data Dragon — język", "Język nazw i opisów postaci.",
                SettingType.STRING, ddragon::locale));
        all.add(SettingDefinition.readOnly("ddragon.sync-on-startup", "DDRAGON_SYNC_ON_STARTUP", GROUP_STARTUP,
                "Synchronizacja przy starcie", "Pobieranie listy postaci przy każdym uruchomieniu.",
                SettingType.BOOLEAN, () -> Boolean.toString(ddragon.syncOnStartup())));
        all.add(SettingDefinition.readOnly("bootstrap.admin-username", "APP_ADMIN_USERNAME", GROUP_STARTUP,
                "Login administratora", "Konto zakładane przy pierwszym uruchomieniu.",
                SettingType.STRING, () -> environment.getProperty("app.bootstrap.admin-username", "admin")));
        all.add(SettingDefinition.readOnly("bootstrap.admin-email", "APP_ADMIN_EMAIL", GROUP_STARTUP,
                "E-mail administratora", "Adres konta zakładanego przy pierwszym uruchomieniu.",
                SettingType.STRING, () -> environment.getProperty("app.bootstrap.admin-email", "")));

        all.forEach(definition -> byKey.put(definition.key(), definition));
    }

    public List<SettingDefinition> all() {
        return List.copyOf(byKey.values());
    }

    public SettingDefinition find(String key) {
        return byKey.get(key);
    }
}
