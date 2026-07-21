package pl.romcio.driperska.integration.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.discord")
public class DiscordProperties {
    private String botToken;
    private String guildId;
    private String resultsChannelId;

    public boolean configured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(guildId);
    }
    public boolean resultsChannelConfigured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(resultsChannelId);
    }
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public String getGuildId() { return guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }
    public String getResultsChannelId() { return resultsChannelId; }
    public void setResultsChannelId(String resultsChannelId) { this.resultsChannelId = resultsChannelId; }
}

