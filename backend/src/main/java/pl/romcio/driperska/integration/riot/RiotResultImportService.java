package pl.romcio.driperska.integration.riot;

import java.util.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.concurrent.ThreadLocalRandom;
import pl.romcio.driperska.champion.domain.Champion;
import pl.romcio.driperska.champion.infra.ChampionRepository;
import pl.romcio.driperska.common.domain.Role;
import pl.romcio.driperska.common.domain.Side;
import pl.romcio.driperska.common.error.BusinessRuleException;
import pl.romcio.driperska.integration.riot.RiotApiClient.*;
import pl.romcio.driperska.match.api.MatchDtos.ParticipantResultInput;
import pl.romcio.driperska.match.api.MatchDtos.SubmitResultsRequest;
import pl.romcio.driperska.match.application.ResultService;
import pl.romcio.driperska.match.domain.Match;
import pl.romcio.driperska.match.domain.MatchParticipant;
import pl.romcio.driperska.match.domain.MatchStatus;
import pl.romcio.driperska.match.infra.MatchRepository;
import pl.romcio.driperska.player.domain.Player;
import pl.romcio.driperska.player.infra.PlayerRepository;

@Service
public class RiotResultImportService {
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final RiotApiClient client;
    private final ResultService resultService;
    private final RiotImportStateService stateService;
    private final ChampionRepository championRepository;
    private final RiotProperties properties;

    public RiotResultImportService(MatchRepository matchRepository, PlayerRepository playerRepository,
                                   RiotApiClient client, ResultService resultService,
                                   RiotImportStateService stateService,
                                   ChampionRepository championRepository, RiotProperties properties) {
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.client = client;
        this.resultService = resultService;
        this.stateService = stateService;
        this.championRepository = championRepository;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void importAfterCallback(RiotCallbackService.RiotResultAvailable event) {
        try {
            importNow(event.matchId());
        } catch (RuntimeException ignored) {
            // The failure is persisted for the admin; callback already received HTTP 200.
        }
    }

    public Match importNow(UUID matchId) {
        try {
            Match match = matchRepository.findDetailedById(matchId)
                    .orElseThrow(() -> new BusinessRuleException("Nie znaleziono meczu"));
            if (match.getRiotResultsImportedAt() != null
                    && match.getStatus() == MatchStatus.RESULTS_SUBMITTED) {
                return match;
            }
            if (match.getStatus() != MatchStatus.LIVE) {
                throw new BusinessRuleException("Statystyki Riot można pobrać dopiero dla trwającego meczu");
            }
            String riotMatchId;
            SubmitResultsRequest request;
            if (properties.isMock()) {
                riotMatchId = "MOCK_" + match.getId().toString().replace("-", "").substring(0, 12);
                request = mockRequest(match);
            } else {
                riotMatchId = findRiotMatchId(match);
                RiotMatch riotMatch = client.getMatch(riotMatchId);
                request = toRequest(match, riotMatch);
            }
            Match saved = resultService.saveResults(matchId, request, match.getCreatedBy());
            stateService.success(matchId, riotMatchId);
            return saved;
        } catch (RuntimeException ex) {
            stateService.failure(matchId, ex.getMessage());
            throw ex;
        }
    }

    /** Fabricates a plausible scoreboard for a match so the full flow can be tested without Riot. */
    private SubmitResultsRequest mockRequest(Match match) {
        List<Integer> champs = championRepository.findAll().stream().map(Champion::getId).limit(30).toList();
        if (champs.isEmpty()) {
            throw new BusinessRuleException(
                    "Brak championów w bazie — uruchom synchronizację DDragon, aby wygenerować dane testowe");
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Side winner = rnd.nextBoolean() ? Side.BLUE : Side.RED;
        List<ParticipantResultInput> inputs = new ArrayList<>();
        int i = 0;
        for (MatchParticipant p : match.getParticipants()) {
            boolean won = p.getSide() == winner;
            inputs.add(new ParticipantResultInput(
                    p.getPlayerId(), p.getRole(), champs.get(i % champs.size()),
                    rnd.nextInt(won ? 3 : 0, won ? 14 : 9),   // kills
                    rnd.nextInt(won ? 0 : 3, won ? 7 : 12),   // deaths
                    rnd.nextInt(2, 20),                        // assists
                    rnd.nextInt(120, 320),                     // cs
                    9000 + rnd.nextInt(0, 8000),               // gold
                    8000 + rnd.nextInt(0, 32000),              // damage
                    rnd.nextInt(10, 60),                       // vision
                    rnd.nextInt(1, 4)));                       // largest multi-kill
            i++;
        }
        return new SubmitResultsRequest(winner, 1500 + rnd.nextInt(0, 1500), "MOCK", inputs);
    }

    private String findRiotMatchId(Match match) {
        if (match.getRiotMatchId() != null) return match.getRiotMatchId();
        List<TournamentGame> games = client.getGames(match.getRiotTournamentCode());
        if (games.isEmpty()) {
            throw new BusinessRuleException(
                    "Riot nie zwrócił jeszcze meczu dla tego kodu; spróbuj ponownie za chwilę");
        }
        return client.matchIdFor(String.valueOf(games.getLast().gameId()));
    }

    private SubmitResultsRequest toRequest(Match match, RiotMatch riotMatch) {
        List<Player> localPlayers = playerRepository.findByIdIn(match.getPoolPlayerIds());
        Map<String, Player> byPuuid = new HashMap<>();
        for (Player player : localPlayers) {
            if (player.getRiotPuuid() != null) byPuuid.put(player.getRiotPuuid(), player);
        }
        List<ParticipantResultInput> inputs = new ArrayList<>();
        for (RiotParticipant participant : riotMatch.info().participants()) {
            Player player = byPuuid.get(participant.puuid());
            if (player == null) continue;
            inputs.add(new ParticipantResultInput(player.getId(), role(participant),
                    participant.championId(), participant.kills(), participant.deaths(),
                    participant.assists(), participant.totalMinionsKilled()
                            + participant.neutralMinionsKilled(),
                    participant.goldEarned(), participant.totalDamageDealtToChampions(),
                    participant.visionScore(), participant.largestMultiKill()));
        }
        if (inputs.size() != match.getPoolPlayerIds().size()) {
            throw new BusinessRuleException(
                    "Nie udało się przypisać wszystkich statystyk Riot do graczy portalu ("
                            + inputs.size() + "/" + match.getPoolPlayerIds().size() + ")");
        }
        Side winningSide = riotMatch.info().teams().stream().filter(RiotTeam::win)
                .findFirst().map(team -> team.teamId() == 100 ? Side.BLUE : Side.RED)
                .orElseThrow(() -> new BusinessRuleException("Riot nie zwrócił zwycięskiej drużyny"));
        int duration = Math.toIntExact(riotMatch.info().gameDuration());
        return new SubmitResultsRequest(winningSide, duration,
                shortPatch(riotMatch.info().gameVersion()), inputs);
    }

    private static Role role(RiotParticipant participant) {
        String value = participant.teamPosition();
        if (value == null || value.isBlank()) value = participant.individualPosition();
        return switch (value == null ? "" : value) {
            case "TOP" -> Role.TOP;
            case "JUNGLE" -> Role.JUNGLE;
            case "MIDDLE" -> Role.MID;
            case "BOTTOM" -> Role.ADC;
            case "UTILITY" -> Role.SUPPORT;
            default -> Role.MID;
        };
    }

    private static String shortPatch(String version) {
        if (version == null) return null;
        String[] parts = version.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : version;
    }
}

