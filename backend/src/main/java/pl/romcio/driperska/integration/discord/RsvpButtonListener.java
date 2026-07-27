package pl.romcio.driperska.integration.discord;

import java.util.UUID;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.match.application.PlannedMatchService;

/**
 * Turns RSVP button clicks on planned-match messages into votes. Only clicks from Discord
 * accounts linked to a player (player.discord_user_id) are counted; everyone else gets an
 * ephemeral refusal. Answers are ephemeral so the channel stays clean and votes stay private.
 */
class RsvpButtonListener extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(RsvpButtonListener.class);
    private final PlannedMatchService plannedMatchService;

    RsvpButtonListener(PlannedMatchService plannedMatchService) {
        this.plannedMatchService = plannedMatchService;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id == null || !id.startsWith("rsvp:")) {
            return;
        }
        String[] parts = id.split(":");
        if (parts.length != 3) {
            return;
        }
        event.deferReply(true).queue(); // ephemeral ack — only the voter sees the outcome
        String response = parts[2];
        try {
            UUID plannedMatchId = UUID.fromString(parts[1]);
            String nickname = plannedMatchService.rsvpByDiscord(
                    plannedMatchId, event.getUser().getId(), response);
            event.getHook().sendMessage(
                    "✅ Zapisano głos: **" + label(response) + "** (gracz: " + nickname + ")")
                    .setEphemeral(true).queue();
        } catch (BusinessRuleException ex) {
            event.getHook().sendMessage("⚠️ " + ex.getMessage()).setEphemeral(true).queue();
        } catch (Exception ex) {
            log.warn("Discord RSVP vote failed (component {})", id, ex);
            event.getHook().sendMessage("⚠️ Nie udało się zapisać głosu — zagłosuj przez stronę.")
                    .setEphemeral(true).queue();
        }
    }

    private static String label(String response) {
        return switch (response) {
            case "YES" -> "Będę";
            case "NO" -> "Nie będę";
            default -> "Może";
        };
    }
}
