package pl.romcio.driperska.integration.discord;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordPropertiesTest {

    @Test
    void patchNotesRequireDedicatedChannel() {
        DiscordProperties properties = new DiscordProperties();
        properties.setBotToken("token");
        properties.setResultsChannelId("results");
        properties.setAnnounceChannelId("announcements");

        assertThat(properties.patchNotesChannel()).isNull();
        assertThat(properties.patchNotesChannelConfigured()).isFalse();

        properties.setPatchNotesChannelId("patch-notes");

        assertThat(properties.patchNotesChannel()).isEqualTo("patch-notes");
        assertThat(properties.patchNotesChannelConfigured()).isTrue();
    }
}