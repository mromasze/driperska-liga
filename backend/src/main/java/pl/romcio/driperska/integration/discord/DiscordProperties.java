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
    private String announceChannelId;
    private String patchNotesChannelId;

    public boolean configured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(guildId);
    }
    public boolean resultsChannelConfigured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(resultsChannelId);
    }
    /** Channel for announcements — falls back to the results channel when not set separately. */
    public String announceChannel() {
        return StringUtils.hasText(announceChannelId) ? announceChannelId : resultsChannelId;
    }
    public boolean announceChannelConfigured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(announceChannel());
    }
    /** Channel for patch notes — falls back to the announcements channel when not set separately. */
    public String patchNotesChannel() {
        return StringUtils.hasText(patchNotesChannelId) ? patchNotesChannelId : announceChannel();
    }
    public boolean patchNotesChannelConfigured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(patchNotesChannel());
    }
    public String getBotToken() { return botToken; }
    public void setBotToken(String botToken) { this.botToken = botToken; }
    public String getGuildId() { return guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }
    public String getResultsChannelId() { return resultsChannelId; }
    public void setResultsChannelId(String resultsChannelId) { this.resultsChannelId = resultsChannelId; }
    public String getAnnounceChannelId() { return announceChannelId; }
    public void setAnnounceChannelId(String announceChannelId) { this.announceChannelId = announceChannelId; }
    public String getPatchNotesChannelId() { return patchNotesChannelId; }
    public void setPatchNotesChannelId(String patchNotesChannelId) { this.patchNotesChannelId = patchNotesChannelId; }
}

