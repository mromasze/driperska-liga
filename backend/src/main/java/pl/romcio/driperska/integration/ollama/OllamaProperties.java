package pl.romcio.driperska.integration.ollama;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Ollama (Cloud) settings for reading LoL end-game screenshots. Point {@code visionModel} at a
 * vision-capable model available in your Ollama Cloud account (see https://ollama.com/settings/keys).
 */
@Component
@ConfigurationProperties(prefix = "app.ollama")
public class OllamaProperties {
    private String baseUrl = "https://ollama.com";
    private String apiKey;
    private String visionModel = "gemma4:31b-cloud";
    private int timeoutSeconds = 240;
    /**
     * Attach a labelled champion-portrait atlas to help name recognition. Off by default: it adds
     * two large images to every request, which drastically slows hosted models (and can time out).
     */
    private boolean atlasEnabled = false;

    public boolean configured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(visionModel);
    }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getVisionModel() { return visionModel; }
    public void setVisionModel(String visionModel) { this.visionModel = visionModel; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public boolean isAtlasEnabled() { return atlasEnabled; }
    public void setAtlasEnabled(boolean atlasEnabled) { this.atlasEnabled = atlasEnabled; }
}
