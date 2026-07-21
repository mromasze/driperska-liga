package pl.romcio.driperska.integration.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "app.discord")
public class DiscordProperties {
    private String botToken;
    private String guildId;

    public boolean configured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(guildId);
    }
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public String getGuildId() { return guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }
}

