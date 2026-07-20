package pl.romcio.driperska.player.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String mediaDir,
        String publicBaseUrl) {

    public StorageProperties {
        if (mediaDir == null || mediaDir.isBlank()) {
            mediaDir = "./data/media";
        }
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            publicBaseUrl = "/media";
        }
    }
}
