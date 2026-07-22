package pl.romcio.driperska.diagnostics;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pl.romcio.driperska.integration.discord.DiscordProperties;
import pl.romcio.driperska.integration.ollama.OllamaProperties;
import pl.romcio.driperska.integration.riot.RiotProperties;

/** Lightweight connectivity checks for the admin diagnostics panel. */
@Service
public class DiagnosticsService {

    public record ServiceHealth(boolean ok, boolean configured, String message) {}

    private final RiotProperties riot;
    private final DiscordProperties discord;
    private final OllamaProperties ollama;
    private final RestClient client;

    public DiagnosticsService(RiotProperties riot, DiscordProperties discord, OllamaProperties ollama) {
        this.riot = riot;
        this.discord = discord;
        this.ollama = ollama;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(6).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.client = RestClient.builder().requestFactory(f).build();
    }

    public ServiceHealth checkOllama() {
        if (!ollama.configured()) {
            return new ServiceHealth(false, false, "Brak OLLAMA_API_KEY / OLLAMA_VISION_MODEL");
        }
        try {
            client.get().uri(pl.romcio.driperska.integration.ollama.OllamaVisionClient.nativeBase(ollama.getBaseUrl()) + "/api/tags")
                    .header("Authorization", "Bearer " + ollama.getApiKey())
                    .retrieve().toBodilessEntity();
            return new ServiceHealth(true, true, "Połączenie OK. Model: " + ollama.getVisionModel());
        } catch (RestClientResponseException ex) {
            int s = ex.getStatusCode().value();
            String msg = s == 401 ? "Nieprawidłowy OLLAMA_API_KEY" : "Ollama zwróciła HTTP " + s;
            return new ServiceHealth(false, true, msg);
        } catch (RestClientException ex) {
            return new ServiceHealth(false, true, "Brak połączenia z Ollama (timeout/sieć)");
        }
    }

    public ServiceHealth checkDiscord() {
        if (discord.getBotToken() == null || discord.getBotToken().isBlank()) {
            return new ServiceHealth(false, false, "Brak DISCORD_BOT_TOKEN");
        }
        try {
            String me = client.get().uri("https://discord.com/api/v10/users/@me")
                    .header("Authorization", "Bot " + discord.getBotToken())
                    .retrieve().body(String.class);
            String extra = discord.resultsChannelConfigured() ? "" : " (uwaga: brak DISCORD_RESULTS_CHANNEL_ID)";
            String bot = me != null && me.contains("\"username\"")
                    ? me.replaceAll(".*\"username\"\\s*:\\s*\"([^\"]+)\".*", "$1") : "bot";
            return new ServiceHealth(true, true, "Bot połączony jako " + bot + extra);
        } catch (RestClientResponseException ex) {
            int s = ex.getStatusCode().value();
            String msg = (s == 401) ? "Nieprawidłowy DISCORD_BOT_TOKEN" : "Discord zwrócił HTTP " + s;
            return new ServiceHealth(false, true, msg);
        } catch (RestClientException ex) {
            return new ServiceHealth(false, true, "Brak połączenia z Discordem");
        }
    }

    public ServiceHealth checkRiot() {
        if (riot.isMock()) {
            return new ServiceHealth(true, true, "Tryb testowy (RIOT_MOCK=true) — zapytania do Riot są symulowane");
        }
        if (!riot.configured()) {
            return new ServiceHealth(false, false, "Brak RIOT_API_KEY");
        }
        try {
            client.get().uri("https://" + riot.getPlatform() + ".api.riotgames.com/lol/status/v4/platform-data")
                    .header("X-Riot-Token", riot.getApiKey())
                    .retrieve().toBodilessEntity();
            return new ServiceHealth(true, true,
                    "Klucz działa (" + riot.getPlatform() + "). Uwaga: dostęp do Tournament API jest osobny.");
        } catch (RestClientResponseException ex) {
            int s = ex.getStatusCode().value();
            if (s == 429) {
                return new ServiceHealth(true, true, "Klucz działa (limit zapytań 429 — to nie błąd klucza).");
            }
            String msg = (s == 401 || s == 403) ? "Klucz nieważny lub bez dostępu (HTTP " + s + ")"
                    : "Riot zwrócił HTTP " + s;
            return new ServiceHealth(false, true, msg);
        } catch (RestClientException ex) {
            return new ServiceHealth(false, true, "Brak połączenia z Riot API");
        }
    }
}
