package pl.romcio.driperska.integration.discord;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.EnumSet;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.romcio.driperska.match.application.PlannedMatchService;

/**
 * Discord gateway (websocket) connection — receives RSVP vote button clicks from the vote
 * channel. Sending messages stays on the plain REST {@link DiscordClient}; this bean only
 * listens. Disabled when no bot token is configured (local dev, tests).
 */
@Component
public class DiscordRsvpGateway {
    private static final Logger log = LoggerFactory.getLogger(DiscordRsvpGateway.class);
    private final DiscordProperties properties;
    private final PlannedMatchService plannedMatchService;
    private JDA jda;

    public DiscordRsvpGateway(DiscordProperties properties, PlannedMatchService plannedMatchService) {
        this.properties = properties;
        this.plannedMatchService = plannedMatchService;
    }

    @PostConstruct
    void start() {
        if (!properties.configured()) {
            log.info("Discord gateway disabled — no DISCORD_BOT_TOKEN/DISCORD_GUILD_ID, Discord voting off");
            return;
        }
        try {
            // Button interactions arrive without any gateway intents — no privileged intents needed.
            jda = JDABuilder.createLight(properties.getBotToken(), EnumSet.noneOf(GatewayIntent.class))
                    .addEventListeners(new RsvpButtonListener(plannedMatchService))
                    .build();
            log.info("Discord gateway connecting — RSVP vote channel: {}", properties.voteChannel());
        } catch (Exception ex) {
            jda = null;
            log.warn("Discord gateway failed to start — Discord voting is off", ex);
        }
    }

    @PreDestroy
    void stop() {
        if (jda != null) {
            jda.shutdown();
        }
    }
}
