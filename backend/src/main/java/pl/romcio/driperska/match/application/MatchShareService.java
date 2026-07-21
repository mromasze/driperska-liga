package pl.romcio.driperska.match.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.common.error.ResourceNotFoundException;
import pl.romcio.driperska.integration.discord.DiscordClient;
import pl.romcio.driperska.integration.discord.DiscordClient.Delivery;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchEventType;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

/** Builds a result image for a match and shares it to the Discord results channel. */
@Service
public class MatchShareService {
    private static final Map<Role, Integer> ROLE_ORDER = Map.of(
            Role.TOP, 0, Role.JUNGLE, 1, Role.MID, 2, Role.ADC, 3, Role.SUPPORT, 4);

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final ChampionRepository championRepository;
    private final ResultImageGenerator imageGenerator;
    private final DiscordClient discordClient;
    private final MatchEventRecorder eventRecorder;

    public MatchShareService(MatchRepository matchRepository, PlayerRepository playerRepository,
                             ChampionRepository championRepository, ResultImageGenerator imageGenerator,
                             DiscordClient discordClient, MatchEventRecorder eventRecorder) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.championRepository = championRepository;
        this.imageGenerator = imageGenerator;
        this.discordClient = discordClient;
        this.eventRecorder = eventRecorder;
    }

    @Transactional(readOnly = true)
    public byte[] renderImage(UUID matchId) {
        Match match = matchRepository.findDetailedById(matchId)
                .orElseThrow(() -> ResourceNotFoundException.of("Match", matchId));
        if (match.getParticipants().isEmpty()) {
            throw new BusinessRuleException("Mecz nie ma jeszcze wpisanego składu/statystyk");
        }
        return imageGenerator.render(toCard(match));
    }

    @Transactional
    public ShareResult shareToDiscord(UUID matchId, UUID actor) {
        byte[] png = renderImage(matchId);
        Match match = matchRepository.findById(matchId).orElseThrow();
        int blue = sideKills(match, Side.BLUE);
        int red = sideKills(match, Side.RED);
        String caption = "🏆 **Driperska Liga** — wynik meczu\nNiebiescy **" + blue + "** : **" + red + "** Czerwoni";
        Delivery delivery = discordClient.sendResultImage(caption, png, "wynik-" + matchId + ".png");
        if (delivery.sent()) {
            eventRecorder.record(matchId, MatchEventType.DISCORD_SHARED, actor, null);
        }
        return new ShareResult(delivery.sent(), delivery.message());
    }

    private ResultImageGenerator.Card toCard(Match match) {
        List<Player> players = playerRepository.findByIdIn(match.getPoolPlayerIds());
        Map<UUID, String> nicks = new HashMap<>();
        players.forEach(p -> nicks.put(p.getId(), p.getNickname()));
        Map<Integer, String> champs = new HashMap<>();
        championRepository.findAll().forEach(c -> champs.put(c.getId(), c.getName()));

        List<ResultImageGenerator.Row> blue = rowsFor(match, Side.BLUE, nicks, champs);
        List<ResultImageGenerator.Row> red = rowsFor(match, Side.RED, nicks, champs);
        String subtitle = "Czas: " + clock(match.getDurationSeconds())
                + (match.getPatch() != null ? "  ·  patch " + match.getPatch() : "");
        return new ResultImageGenerator.Card("Driperska Liga — Wynik meczu", subtitle,
                match.getWinningSide() == Side.BLUE, match.getWinningSide() != null,
                sideKills(match, Side.BLUE), sideKills(match, Side.RED), blue, red);
    }

    private List<ResultImageGenerator.Row> rowsFor(Match match, Side side, Map<UUID, String> nicks,
                                                   Map<Integer, String> champs) {
        List<ResultImageGenerator.Row> rows = new ArrayList<>();
        match.getParticipants().stream()
                .filter(p -> p.getSide() == side)
                .sorted(Comparator.comparingInt(p -> ROLE_ORDER.getOrDefault(p.getRole(), 9)))
                .forEach(p -> rows.add(new ResultImageGenerator.Row(
                        nicks.getOrDefault(p.getPlayerId(), "?"),
                        p.getRole() == null ? "" : p.getRole().name(),
                        p.getChampionId() == null ? "—" : champs.getOrDefault(p.getChampionId(), "—"),
                        p.getKills(), p.getDeaths(), p.getAssists(), p.getCs(),
                        p.getPerformanceRating() == null ? null : (int) Math.round(p.getPerformanceRating()),
                        p.isMvp())));
        return rows;
    }

    private static int sideKills(Match match, Side side) {
        return match.getParticipants().stream().filter(p -> p.getSide() == side)
                .mapToInt(MatchParticipant::getKills).sum();
    }

    private static String clock(Integer seconds) {
        if (seconds == null) return "—";
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    public record ShareResult(boolean sent, String message) {}
}
