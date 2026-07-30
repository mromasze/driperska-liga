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
    private String voteChannelId;
    private String moderationChannelId;

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
    /** Dedicated channel for patch notes. */
    public String patchNotesChannel() {
        return patchNotesChannelId;
    }
    public boolean patchNotesChannelConfigured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(patchNotesChannel());
    }
    /** Channel for RSVP vote messages — falls back to the announcements channel when not set separately. */
    public String voteChannel() {
        return StringUtils.hasText(voteChannelId) ? voteChannelId : announceChannel();
    }
    public boolean voteChannelConfigured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(voteChannel());
    }
    /**
     * Channel the bot pings when a moderator sends a match to the approval queue. Falls back to the
     * announcements channel; that message never mentions @everyone, so a shared channel is bearable,
     * but a private admin channel is the better setup.
     */
    public String moderationChannel() {
        return StringUtils.hasText(moderationChannelId) ? moderationChannelId : announceChannel();
    }
    public boolean moderationChannelConfigured() {
        return StringUtils.hasText(botToken) && StringUtils.hasText(moderationChannel());
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
    public String getVoteChannelId() { return voteChannelId; }
    public void setVoteChannelId(String voteChannelId) { this.voteChannelId = voteChannelId; }
    public String getModerationChannelId() { return moderationChannelId; }
    public void setModerationChannelId(String moderationChannelId) {
        this.moderationChannelId = moderationChannelId;
    }
}

