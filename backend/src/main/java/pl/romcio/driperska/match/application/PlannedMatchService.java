package pl.romcio.driperska.match.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.integration.discord.DiscordClient;
import pl.romcio.driperska.integration.discord.DiscordClient.Delivery;
import pl.romcio.driperska.match.api.PlannedMatchDtos.CreatePlannedMatchResult;
import pl.romcio.driperska.match.api.PlannedMatchDtos.PlannedMatchResponse;
import pl.romcio.driperska.match.api.PlannedMatchDtos.RsvpEntry;
import pl.romcio.driperska.match.domain.PlannedMatch;
import pl.romcio.driperska.match.infra.PlannedMatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

@Service
public class PlannedMatchService {
    private static final Set<String> RESPONSES = Set.of("YES", "NO", "MAYBE");

    private final PlannedMatchRepository repository;
    private final PlayerRepository playerRepository;
    private final DiscordClient discordClient;
    private final String publicUrl;

    public PlannedMatchService(PlannedMatchRepository repository, PlayerRepository playerRepository,
                               DiscordClient discordClient,
                               @Value("${app.public-url:https://driperska.pl}") String publicUrl) {
        this.repository = repository;
        this.playerRepository = playerRepository;
        this.discordClient = discordClient;
        this.publicUrl = publicUrl.replaceAll("/$", "");
    }

    @Transactional
    public CreatePlannedMatchResult create(Instant scheduledAt, String note, UUID actor) {
        if (scheduledAt == null) throw new BusinessRuleException("Podaj termin meczu");
        PlannedMatch planned = repository.save(new PlannedMatch(scheduledAt, note, actor));
        Delivery delivery = discordClient.sendAnnouncement(announcement(planned));
        Delivery vote = discordClient.sendRsvpMessage(voteMessage(planned), planned.getId());
        String message = delivery.message();
        if (!vote.sent()) {
            message += " | Głosowanie Discord: " + vote.message();
        }
        return new CreatePlannedMatchResult(toResponse(planned, null), delivery.sent(), message);
    }

    @Transactional
    public PlannedMatchResponse rsvp(UUID id, UUID accountId, String response) {
        String r = response == null ? "" : response.trim().toUpperCase(java.util.Locale.ROOT);
        if (!RESPONSES.contains(r)) throw new BusinessRuleException("Nieprawidłowa odpowiedź");
        PlannedMatch planned = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("PlannedMatch", id));
        if (!PlannedMatch.PLANNED.equals(planned.getStatus())) {
            throw new BusinessRuleException("Ten mecz nie jest już planowany");
        }
        Player player = playerRepository.findByAccountId(accountId)
                .orElseThrow(() -> new BusinessRuleException("Konto nie jest połączone z graczem"));
        planned.setResponse(player.getId(), r);
        return toResponse(planned, accountId);
    }

    /**
     * RSVP cast from Discord (vote-channel button). Only votes from Discord accounts linked to
     * a player count. Returns the player's nickname for the ephemeral confirmation.
     */
    @Transactional
    public String rsvpByDiscord(UUID id, String discordUserId, String response) {
        String r = response == null ? "" : response.trim().toUpperCase(java.util.Locale.ROOT);
        if (!RESPONSES.contains(r)) throw new BusinessRuleException("Nieprawidłowa odpowiedź");
        PlannedMatch planned = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("PlannedMatch", id));
        if (!PlannedMatch.PLANNED.equals(planned.getStatus())) {
            throw new BusinessRuleException("Ten mecz nie jest już planowany");
        }
        Player player = playerRepository.findByDiscordUserId(discordUserId)
                .orElseThrow(() -> new BusinessRuleException(
                        "Twoje konto Discord nie jest połączone z graczem — zagłosuj przez stronę"));
        planned.setResponse(player.getId(), r);
        return player.getNickname();
    }

    @Transactional(readOnly = true)
    public List<PlannedMatchResponse> listUpcoming(UUID viewerAccountId) {
        return repository.findByStatusOrderByScheduledAtAsc(PlannedMatch.PLANNED).stream()
                .map(p -> toResponse(p, viewerAccountId)).toList();
    }

    @Transactional
    public void cancel(UUID id) {
        PlannedMatch planned = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("PlannedMatch", id));
        planned.cancel();
    }

    private PlannedMatchResponse toResponse(PlannedMatch planned, UUID viewerAccountId) {
        List<UUID> ids = planned.getRsvps().stream().map(PlannedMatch.Rsvp::getPlayerId).toList();
        Map<UUID, String> nicks = new HashMap<>();
        if (!ids.isEmpty()) {
            playerRepository.findByIdIn(ids).forEach(p -> nicks.put(p.getId(), p.getNickname()));
        }
        UUID viewerPlayerId = viewerAccountId == null ? null
                : playerRepository.findByAccountId(viewerAccountId).map(Player::getId).orElse(null);
        int yes = 0, no = 0, maybe = 0;
        String mine = null;
        List<RsvpEntry> entries = new ArrayList<>();
        for (PlannedMatch.Rsvp r : planned.getRsvps()) {
            switch (r.getResponse()) {
                case "YES" -> yes++;
                case "NO" -> no++;
                default -> maybe++;
            }
            if (viewerPlayerId != null && viewerPlayerId.equals(r.getPlayerId())) mine = r.getResponse();
            entries.add(new RsvpEntry(r.getPlayerId(), nicks.getOrDefault(r.getPlayerId(), "?"), r.getResponse()));
        }
        return new PlannedMatchResponse(planned.getId(), planned.getScheduledAt(), planned.getNote(),
                planned.getStatus(), planned.getCreatedAt(), yes, no, maybe, mine, entries);
    }

    private String announcement(PlannedMatch planned) {
        long epoch = planned.getScheduledAt().getEpochSecond();
        StringBuilder sb = new StringBuilder();
        sb.append("📅 **Zaplanowano mecz Driperskiej Ligi**\n");
        sb.append("🕒 <t:").append(epoch).append(":F> (<t:").append(epoch).append(":R>)\n");
        if (planned.getNote() != null && !planned.getNote().isBlank()) {
            sb.append("📝 ").append(planned.getNote()).append('\n');
        }
        sb.append("✅ Potwierdź obecność po zalogowaniu: ").append(publicUrl).append("/panel\n");
        sb.append("@everyone");
        return sb.toString();
    }

    private String voteMessage(PlannedMatch planned) {
        long epoch = planned.getScheduledAt().getEpochSecond();
        StringBuilder sb = new StringBuilder();
        sb.append("🗳️ **Kto gra? — mecz <t:").append(epoch).append(":F> (<t:").append(epoch).append(":R>)**\n");
        if (planned.getNote() != null && !planned.getNote().isBlank()) {
            sb.append("📝 ").append(planned.getNote()).append('\n');
        }
        sb.append("Kliknij przycisk poniżej — liczą się tylko głosy kont Discord połączonych z graczem.\n");
        sb.append("Głos możesz zmienić klikając ponownie. Wyniki: ").append(publicUrl).append("/panel");
        return sb.toString();
    }
}
