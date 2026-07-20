package pl.romcio.driperska.champion.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ddragon")
public record DataDragonProperties(
        String baseUrl,
        String locale,
        boolean syncOnStartup) {

    public DataDragonProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://ddragon.leagueoflegends.com";
        }
        if (locale == null || locale.isBlank()) {
            locale = "en_US";
        }
    }
}
