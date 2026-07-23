package pl.romcio.driperska.champion.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Thin client over Riot's Data Dragon static-data CDN (no API key required). */
@Component
@EnableConfigurationProperties(DataDragonProperties.class)
public class DataDragonClient {

    private final RestClient restClient;
    private final DataDragonProperties properties;

    public DataDragonClient(DataDragonProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /** Latest available game version (patch), e.g. {@code "14.13.1"}. */
    public String latestVersion() {
        String[] versions = restClient.get()
                .uri("/api/versions.json")
                .retrieve()
                .body(String[].class);
        if (versions == null || versions.length == 0) {
            throw new IllegalStateException("Data Dragon returned no versions");
        }
        return versions[0];
    }

    public ChampionListResponse fetchChampions(String version) {
        return restClient.get()
                .uri("/cdn/{ver}/data/{locale}/champion.json", version, properties.locale())
                .retrieve()
                .body(ChampionListResponse.class);
    }

    public String iconUrl(String version, String slug) {
        return "%s/cdn/%s/img/champion/%s.png".formatted(properties.baseUrl(), version, slug);
    }

    /** Downloads the square portrait used by the game UI and scoreboard. */
    public byte[] fetchChampionIcon(String version, String slug) {
        return restClient.get()
                .uri("/cdn/{ver}/img/champion/{slug}.png", version, slug)
                .retrieve()
                .body(byte[].class);
    }

    public String splashUrl(String slug) {
        return "%s/cdn/img/champion/splash/%s_0.jpg".formatted(properties.baseUrl(), slug);
    }

    public String loadingUrl(String slug) {
        return "%s/cdn/img/champion/loading/%s_0.jpg".formatted(properties.baseUrl(), slug);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChampionListResponse(String version, Map<String, ChampionData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChampionData(String key, String id, String name, String title, List<String> tags) {
    }
}
